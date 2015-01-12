package m

import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory }
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import spray.http.HttpRequest
import spray.can.websocket.FrameCommandFailed
import spray.routing.HttpServiceActor
import spray.routing.{ExceptionHandler, HttpService}

object SimpleServer extends App {

  final case class Push(msg: String)

  object WebSocketServer {
    def props() = Props(classOf[WebSocketServer])
  }
  class WebSocketServer extends Actor with ActorLogging {
    def receive = {
      // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
      case Http.Connected(remoteAddress, localAddress) =>
        println("hi")
        val serverConnection = sender()
        val conn = context.actorOf(WebSocketWorker.props(serverConnection))
        serverConnection ! Http.Register(conn)
    }
  }

  object WebSocketWorker {
    def props(serverConnection: ActorRef) = Props(classOf[WebSocketWorker], serverConnection)
  }
  class WebSocketWorker(val serverConnection: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {
    override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic
    var messages: Int = 0

    def businessLogic: Receive = {
      // just bounce frames back for Autobahn testsuite
      case x : TextFrame =>
        
        
        println(s"Received ${x.payload.decodeString("UTF-8")}")
        
        messages += 1
        sender() ! TextFrame(s"booger ${messages}")
      case x : BinaryFrame =>
        println(s"Received ${x}")
        sender() ! x

      case Push(msg) => send(TextFrame(s"Your mom says: ${msg}"))

      case x: FrameCommandFailed =>
        log.error("frame command failed", x)

      case x: HttpRequest => // do something
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
