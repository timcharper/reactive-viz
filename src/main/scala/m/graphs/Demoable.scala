package m.graphs

import akka.actor.ActorRef
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

trait DemoableFlow {
  def flow(listener: ActorRef)(implicit system: ActorSystem): Unit
  def apply(listener: ActorRef): Props = {
    implicit val system = ActorSystem("job")
    flow(listener)
    Props(new Actor {
      def receive = {
        case 'stop =>
          println("stopping the graph")
          system.shutdown()
      }
    })
  }
}
