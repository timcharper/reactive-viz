package m

import akka.actor.PoisonPill
import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket
import spray.can.websocket.frame.Frame
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import spray.http.HttpRequest
import spray.can.websocket.FrameCommandFailed
import spray.routing.HttpServiceActor
import spray.routing.{ExceptionHandler, HttpService}

import play.api.libs.json._

case class WebsocketPush(msg: String)
case class WebsocketReceived(msg: String)
case class RoutedMessage(topic: String, payload: JsValue)

object RoutedMessageFormat {
  import play.api.libs.functional.syntax._
  implicit val routedMessageReads: Reads[RoutedMessage] = (
    (JsPath)(0).read[String] and
    (JsPath)(1).read[JsValue]
  )(RoutedMessage.apply _)

  implicit val routedMessageWrites = Writes[RoutedMessage] { (message) =>
    Json.arr(message.topic, message.payload)
  }
}

class WebSocketWorker(val serverConnection: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {
  import RoutedMessageFormat._

  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic
  var messages: Int = 0
  var websocketChild: Option[ActorRef] = None

  override def postStop: Unit = {
    println("shutting down; child actor should shut down along with this")
    // websocketChild.map { c => c ! PoisonPill }
    super.postStop()
  }

  def businessLogic: Receive = {
    case websocket.UpgradedToWebSocket =>
      websocketChild = Some(context.actorOf(Props(new GraphInstance(self))))
      println("Websocket opened!!!")

    case x : Frame =>
      println(s"receive: ${x.payload.utf8String}")
      websocketChild map { _ !  Json.parse(x.payload.utf8String).as[RoutedMessage] }

    case m : RoutedMessage =>
      send(TextFrame(Json.toJson(m).toString))

    case x: FrameCommandFailed =>
      log.error("frame command failed", x)

    case x: HttpRequest => // do something
                           // case akka.io.Tcp.Closed =>
                           //   context.
  }

  def businessLogicNoUpgrade: Receive = {
    implicit val refFactory: ActorRefFactory = context
    runRoute {
      path("hi") {
        complete("yup")
      } ~
      getFromDirectory("dagre") ~
      getFromResourceDirectory("webapp")
    }
  }
}

object HttpServer extends App {

  object WebSocketServer {
    def props() = Props(classOf[WebSocketServer])
  }
  class WebSocketServer extends Actor with ActorLogging {
    def receive = {
      // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
      case Http.Connected(remoteAddress, localAddress) =>
        val serverConnection = sender()
        val conn = context.actorOf(Props(new WebSocketWorker(serverConnection)))
        serverConnection ! Http.Register(conn)
    }
  }

  def doMain() {
    implicit val system = ActorSystem()
    import system.dispatcher

    val server = system.actorOf(WebSocketServer.props(), "websocket")

    IO(UHttp) ! Http.Bind(server, "localhost", 8080)

    // readLine("Hit ENTER to exit ...\n")
    // system.shutdown()
    // system.awaitTermination()
  }

  // because otherwise we get an ambiguous implicit if doMain is inlined
  doMain()
}
