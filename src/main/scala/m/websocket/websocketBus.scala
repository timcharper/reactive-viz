package m.websocket

import akka.actor.{ActorRef , PoisonPill}
import akka.event.{EventBus, SubchannelClassification}
import akka.util.Subclassification
import play.api.libs.json._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import spray.can.websocket.FrameCommand
import spray.can.websocket.frame.{Frame,TextFrame}

case class RoutedMessage(topic: String, payload: JsValue)

class WebsocketIO(serverConnection: ActorRef) {
  import RoutedMessageFormat._

  private val closedPromise = Promise[Unit]
  val closed = closedPromise.future
  protected [websocket] def markClosed(): Unit = closedPromise.trySuccess(())
  def terminateOnClose(actorRef: ActorRef): Unit =
    closed.onComplete { _ => actorRef ! PoisonPill }
  protected [websocket] val in = new WebsocketEventBus
  def !(m: RoutedMessage): Unit = {
    serverConnection ! FrameCommand(TextFrame(Json.toJson(m).toString))
  }

  def subscribe(actorRef: ActorRef, to: String) = in.subscribe(actorRef, to)

  protected [websocket] def emitIncomingMessage(m: Frame) = {
    in.publish(Json.parse(m.payload.utf8String).as[RoutedMessage])
  }
}

class WebsocketEventBus extends EventBus with SubchannelClassification {
  type Event = RoutedMessage
  type Classifier = String
  type Subscriber = ActorRef

  protected val subclassification = new Subclassification[String] {
    def isEqual(x: String, y: String): Boolean =
      x == y
    // val isEqual = ((_: String) == (_: String))
    
    def isSubclass(subscription: String, eventClassification: String): Boolean =
      if (subscription == "*")
        true
      else
        subscription == eventClassification
  }

  protected def classify(event: Event): Classifier = event.topic
  protected def publish(event: Event, subscriber: Subscriber): Unit = subscriber ! event
}
