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

  def quickFragileScan(p: Package) = {
    Contents.hasFragile(p.contents.take(3))
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

  def delay[T](d: FiniteDuration)(f: => T)(implicit system: ActorSystem): Future[T] = after(d, system.scheduler) { Future(f) }
  def shipFragileHazard(crate: Seq[Package])(implicit system: ActorSystem) = delay(5 seconds) { crate }
  def shipFragile(crate: Seq[Package])(implicit system: ActorSystem) = delay(3 seconds) { crate }
  def shipHazard(crate: Seq[Package])(implicit system: ActorSystem) = delay(2 seconds) { crate }
  def ship(crate: Seq[Package])(implicit system: ActorSystem) = delay(1 seconds) { crate }

  def fitsInHazardScanMachine(pkg: Package): Boolean = pkg.contents.length < 30


  def flow(listener: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val materializer = FlowMaterializer()
    val batches = FileUtils.iterateFiles(new File("./packages/"), Array("json.ld"), true).toList.sortBy(_.toString)

    val a = IntrospectableFlow(listener, Source(batches)).
      map { f => IntrospectableFlow(listener,
        Source(() =>
          new AutoClosingLineIterator(f))) }.
      flatten(FlattenStrategy.concat[String]).
      map(Json.parse(_).as[Package]).
      // Is it labeled for hazardous?
      groupBy { pkg =>
        if (pkg.hazardous.nonEmpty) 'hasHazardLabel else 'noHazardLabel }.
      map {
        case ('hasHazardLabel, flow) => flow.map(identity) // send some frequent shipper points ?
        case ('noHazardLabel, flow) =>
          // can it fit in our hazard detection machine?
          flow.
            groupBy { pkg => if (fitsInHazardScanMachine(pkg)) 'sensorTestable else 'inspectionNeeded }.
            map {
              case ('inspectionNeeded, flow) => flow.mapAsyncUnordered(longHazardScan)
              case ('sensorTestable, flow) =>   flow.map(quickHazardScan)
            }.
            mergeUnordered
      }.
      mergeUnordered.

      // map(Json.toJson(_)).
      // foreach(println)

      groupBy { pkg =>
        if (pkg.fragile.nonEmpty) 'hasFragileLabel else 'noFragileLabel
      }.
      map {
        case ('hasFragileLabel, flow) => flow.map(identity) // send more frequent shipper points ?
        case ('noFragileLabel, flow) =>
          flow.
            groupBy { pkg => if (quickFragileScan(pkg)) 'obvious else 'inspectionNeeded }.
            map {
              case ('obvious, flow) => flow.map { p => p.copy(fragile = Some(true)) }
              case ('inspectionNeeded, flow) => flow.mapAsyncUnordered(longFragileScan)
            }.
            mergeUnordered
      }.
      mergeUnordered.

      // // map(Json.toJson(_)).
      // // foreach(println)

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
            foreach { cargo => println(s"shipped ${cargo.length} fragileHazard items") }
        case ('fragile, flow) =>
          flow.
            groupedWithin(100, 4 seconds).
            mapAsync { shipFragile }.
            foreach { cargo => println(s"shipped ${cargo.length} fragile items") }
        case ('hazard, flow) =>
          flow.
            groupedWithin(100, 4 seconds).
            mapAsync { shipHazard }.
            foreach { cargo => println(s"shipped ${cargo.length} hazard items") }
        case ('standard, flow) =>
          flow.
            groupedWithin(100, 4 seconds).
            mapAsync { ship }.
            foreach { cargo => println(s"shipped ${cargo.length} normal items") }
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
