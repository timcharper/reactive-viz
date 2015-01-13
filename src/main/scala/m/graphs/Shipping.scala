package m.graphs

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.after
import akka.stream.FlattenStrategy
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import java.io.File
import m.Contents
import m.IntrospectableFlow
import m.lib.AutoClosingLineIterator
import m.lib.MergeUnordered.EnrichedSource
import org.apache.commons.io.FileUtils
import play.api.libs.json._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import m.IntrospectableFlow.Implicits._

case class Package(hazardous: Option[Boolean], fragile: Option[Boolean], contents: List[String])
object ShippingHelpers {
  def label(label: String)(js: JsObject): Option[Boolean] = {
    js.fields.collectFirst {
      case (field, value: JsBoolean) => value.value
    }
  }

  def quickHazardScan(p: Package) = {
    p.copy(hazardous = Some(Contents.hasHazardous(p.contents.take(10))))
  }

  def longHazardScan(p: Package): Future[Package] = Future {
    Thread.sleep(p.contents.length * 10) // large packages take longer
    p.copy(hazardous = Some(Contents.hasHazardous(p.contents.take(10))))
  }

  def longFragileScan(p: Package): Future[Package] = Future {
    Thread.sleep(p.contents.length * 3)
    p.copy(fragile = Some(Contents.hasHazardous(p.contents.take(10))))
  }
}

object Shipping extends DemoableFlow {
  import ShippingHelpers._
  implicit val packageFormat = Json.format[Package]

  def flow(listener: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val materializer = FlowMaterializer()
    val inputFiles = FileUtils.iterateFiles(new File("./packages/"), Array("json.ld"), true).toList.sortBy(_.toString)

    def delay[T](d: FiniteDuration)(f: => T): Future[T] = after(d, system.scheduler) { Future(f) }
    def shipFragileHazard(crate: Seq[Package]): Future[Unit] = delay(5 seconds) {
      println("shipped fragile hazardous")
    }
    def shipFragile(crate: Seq[Package]): Future[Unit] = delay(3 seconds) {
      println("shipped fragile")
    }
    def shipHazard(crate: Seq[Package]): Future[Unit] = delay(2 seconds) {
      println("shipped hazard")
    }
    def ship(crate: Seq[Package]): Future[Unit] = delay(1 seconds) {
      println("shipped normal")
    }

    val a = IntrospectableFlow(listener, Source(inputFiles)).
      map { f => IntrospectableFlow(listener, Source(() => new AutoClosingLineIterator(f))) }.
      flatten(FlattenStrategy.concat[String]).
      map(Json.parse(_).as[Package]).
      groupBy { pkg =>
        if (pkg.hazardous.nonEmpty) 'hasHazardLabel else 'noHazardLabel }.
      map {
        case ('hasHazardLabel, flow) => flow.map(identity) // send some frequent shipper points ?
        case ('noHazardLabel, flow) =>
          flow.
            groupBy { pkg => if (pkg.contents.length > 30) 'inspectionNeeded else 'sensorTestable }.
            map {
              case ('sensorTestable, flow) => flow.mapAsyncUnordered(longHazardScan)
              case ('inspectionNeeded, flow) => flow.map(quickHazardScan)
            }.
            mergeUnordered
      }.
      mergeUnordered.
      groupBy { pkg =>
        if (pkg.fragile.nonEmpty) 'hasFragileLabel else 'noFragileLabel
      }.
      map {
        case ('hasFragileLabel, flow) => flow.map(identity) // send more frequent shipper points ?
        case ('noFragileLabel, flow) =>
          flow.
            groupBy { pkg => if (Contents.hasFragile(pkg.contents.take(3))) 'obvious else 'inspectionNeeded }.
            map {
              case ('obvious, flow) => flow.map { p => p.copy(fragile = Some(true)) }
              case ('inspectionNeeded, flow) => flow.mapAsyncUnordered(longFragileScan)
            }.
            mergeUnordered
      }.
      mergeUnordered.
      groupBy {
        case Package(Some(true),  Some(true),  _) => 'fragileHazard
        case Package(Some(false), Some(true),  _) => 'fragile
        case Package(Some(true),  Some(false), _) => 'hazard
        case Package(Some(false), Some(false), _) => 'standard
      }.
      map {
        case ('fragileHazard, flow) =>
          flow.
            groupedWithin(100, 4 seconds).
            mapAsync { shipFragileHazard }.
            foreach(identity)
        case ('fragile, flow) =>
          flow.
            groupedWithin(100, 4 seconds).
            mapAsync { shipHazard }.
            foreach(identity)
        case ('hazard, flow) =>
          flow.
            groupedWithin(100, 4 seconds).
            mapAsync { shipHazard }.
            foreach(identity)
        case ('standard, flow) =>
          flow.
            groupedWithin(100, 4 seconds).
            mapAsync { ship }.
            foreach(identity)
      }.
      fold(Future.successful()) { (a,b) => a flatMap { _ => b } }.
      flatMap(identity).
      onComplete { result =>
        println("all done")
        println(result)
        system.shutdown()
      }
  }
}
