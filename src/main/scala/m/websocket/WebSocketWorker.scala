package m.websocket

import akka.actor.{ Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import spray.routing.HttpServiceActor
import spray.can.websocket.{WebSocketServerWorker,UpgradedToWebSocket,FrameCommandFailed}
import spray.can.websocket.frame.{ Frame, BinaryFrame, TextFrame }
import spray.http.HttpRequest

class WebSocketWorker(val serverConnection: ActorRef, onWebsocket: WebsocketIO => Unit) extends HttpServiceActor with WebSocketServerWorker {
  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic
  var messages: Int = 0

  val io = new WebsocketIO(serverConnection)
  override def postStop: Unit = {
    println("shutting down; child actor should shut down along with this")
    io.markClosed()
    super.postStop()
  }

  def businessLogic: Receive = {
    case UpgradedToWebSocket =>
      onWebsocket(io)
      println("Websocket opened!!!")

    // parse and forward to the child actor
    case x : Frame =>
      io.emitIncomingMessage(x)

    case x: FrameCommandFailed =>
      log.error("frame command failed", x)

    case x: HttpRequest => // do something
  }

  def businessLogicNoUpgrade: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      getFromDirectory("src/main/js") ~
      getFromDirectory("src/main/resources/webapp")
    }
  }
}
