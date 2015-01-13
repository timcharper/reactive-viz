package m

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import play.api.libs.json._
import scala.concurrent.Future

object RunInstance {

  case object Shutdown

  def newFlow(listener: ActorRef): ActorRef = {
    implicit val system = ActorSystem("job")
    implicit val materializer = FlowMaterializer()
    import system.dispatcher
    import MergeUnordered.EnrichedSource

    IntrospectableFlow(listener, Source(1 to 1000)).
      groupBy { n => (n % 5) == 0 }.
      map {
        case (true, flow) =>
          flow.
            bottleneck(100).
            map { n =>
              s"*$n" }

        case (false, flow) =>
          flow.
            map {n =>
              s"#$n"}
      }.
      mergeUnordered.
      foreach { d =>
        // d.foreach(identity)
        println(s"stream: ${d}")
      }.
      onComplete { result =>
        println("all done")
        println(result)
        system.shutdown()
      }

    system.actorOf(Props(new Actor {
      def receive = {
        case Shutdown =>
          println("stopping the world")
          context.system.shutdown()
      }
    }))
  }
}

object JsonFormats {
  import GraphRegistry._
  implicit val graphPropertiesWriter = Json.format[GraphRegistry.EdgeProperties]
}

class GraphInstance(websocket: ActorRef) extends Actor {
  import IntrospectableFlow._
  import JsonFormats._
  val flow = RunInstance.newFlow(self)

  override def postStop: Unit = {
    println("Stopping instance of graph simulation.")
    flow ! RunInstance.Shutdown
  }
  def receive = {
    case RoutedMessage("ping", value) =>
      websocket ! RoutedMessage("pong", JsNull)

    case GraphRegistry.GraphInitialized =>
      websocket ! RoutedMessage(
        "graph.initialized",
        JsNull)
    case GraphRegistry.NodeRegistered(node) =>
      websocket ! RoutedMessage(
        "graph.new-node",
        Json.obj(
          "id" -> node.id,
          "name" -> node.nodeName)
      )
    case GraphRegistry.EdgeRegistered(from, to, properties) =>
      websocket ! RoutedMessage(
        "graph.new-edge",
        Json.obj(
          "from" -> from.id,
          "to"   -> to.id,
          "properties" -> properties)
      )
    // received from IntrospectableFlow
    case FlowMessage(nodeId, value) =>
      websocket ! RoutedMessage("node.message", Json.obj("nodeId" -> nodeId, "value" -> value.toString))
  }
}
