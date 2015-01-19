package m.graphs

import akka.actor.ActorRef
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

trait DemoableFlow {
  def flow(listener: ActorRef)(implicit system: ActorSystem): Unit
  def apply(listener: ActorRef): ActorRef = {
    implicit val system = ActorSystem("flow-demo")
    flow(listener)
    system.actorOf(
      Props(new Actor {
        override def postStop: Unit = {
          println("stopping the graph")
          system.shutdown()
        }
        def receive = {
          case 'stop => context.stop(self)
        }
      })
    )
  }
}
