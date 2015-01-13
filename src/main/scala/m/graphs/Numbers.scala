package m.graphs

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import m.lib.MergeUnordered.EnrichedSource
import m.IntrospectableFlow
object Numbers extends DemoableFlow {
  def isPrime(n: Int): Boolean = {
    if (n <= 2) return false
    Thread.sleep(10)
    val ceiling = math.sqrt(n.toDouble).toInt
    (2 until ceiling) forall (x => n % x != 0)
  }

  def flow(listener: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val materializer = FlowMaterializer()

    IntrospectableFlow(listener, Source(1 to 10000)).
      grouped(100).
      mapAsyncUnordered { values => Future { values.filter(isPrime)}}.
      mapConcat(identity).
      fold(0)(_ + _).
      map { n =>
        println("result is $n")
      }.
      onComplete { _ =>
        println("all done")
        system.shutdown()
      }
  }
}
