package m

import akka.actor.PoisonPill
import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import akka.io.IO
import spray.can.server.UHttp
import spray.routing.{ExceptionHandler, HttpService}
import play.api.libs.json._
import m.websocket.WebSocketServer
import m.websocket.WebsocketIO
import spray.can.Http

object HttpServer extends App {
  implicit val system = ActorSystem()
  import system.dispatcher

  // To run a different demoable flow, substitute this line:
  val demoableFlow = m.graphs.Numbers
  // val demoableFlow = m.graphs.Shipping

  def allocateGraphInstance(io: WebsocketIO): Unit = {
    println("very allocate")
    val graph = system.actorOf(Props(new GraphInstance(
      io,
      demoableFlow
    )))
    io.terminateOnClose(graph)
  }

  val server = system.actorOf(
    Props(new WebSocketServer(allocateGraphInstance)),
    "websocket")

  IO(UHttp) ! Http.Bind(server, "localhost", 8080)
}
