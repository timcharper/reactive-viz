package m

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object RunInstance {

  def newFlow(listener: ActorRef): Props = {
    implicit val system = ActorSystem("job")
    implicit val materializer = FlowMaterializer()
    import MergeUnordered.EnrichedSource

    IntrospectableFlow(listener, Source(1 to 1000)).
      groupBy { n => (n % 5) == 0 }.
      map {
        case (true, flow) =>
          flow.
            map { n =>
              Thread.sleep(100)
              s"*$n" }

        case (false, flow) =>
          flow.
            map { n =>
              s"#$n"}
      }.
      mergeUnordered.
      foreach { d =>
        println(s"stream: ${d}")
      }.
      onComplete { result =>
        println("all done")
        println(result)
        system.shutdown()
      }

    Props(new Actor {
      def receive = {
        case 'stop =>
          println("stopping the graph")
          system.shutdown()
      }
    })
  }
}
