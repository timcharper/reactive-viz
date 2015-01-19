package m.websocket

import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import spray.can.Http

class WebSocketServer(onWebsocket: WebsocketIO => Unit) extends Actor with ActorLogging {
  def receive = {
    // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
    case Http.Connected(remoteAddress, localAddress) =>
      val serverConnection = sender()
      val conn = context.actorOf(Props(new WebSocketWorker(serverConnection, onWebsocket)))
      serverConnection ! Http.Register(conn)
  }
}


