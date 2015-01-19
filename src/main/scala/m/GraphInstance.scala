package m

import play.api.libs.json._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import m.websocket.RoutedMessage

object JsonFormats {
  import GraphRegistry._
  implicit val graphPropertiesWriter = Json.format[GraphRegistry.EdgeProperties]
}

// Boots up a graph instance
class GraphInstance(websocket: m.websocket.WebsocketIO, flowDemo: m.graphs.DemoableFlow) extends Actor {
  import IntrospectableFlow._
  import JsonFormats._

  // val flow = context.actorOf(graphs.Numbers(self))
  val flow = flowDemo(self)

  override def postStop: Unit = {
    try {
      flow ! 'stop
    } catch {
      case e: Exception =>
        println(s"Very exception $e")
    }
    println("Stopping instance of graph simulation.")
    super.postStop()
  }
  def receive = {

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
