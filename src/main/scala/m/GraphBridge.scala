package m

import play.api.libs.json._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object JsonFormats {
  import GraphRegistry._
  implicit val graphPropertiesWriter = Json.format[GraphRegistry.EdgeProperties]
}

// Boots up a graph instance
class GraphInstance(websocket: ActorRef) extends Actor {
  import IntrospectableFlow._
  import JsonFormats._
  val flow = context.actorOf(graphs.Numbers(self))
  // val flow = context.actorOf(graphs.Shipping(self))

  override def postStop: Unit = {
    println("Stopping instance of graph simulation.")
    flow ! 'stop
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
