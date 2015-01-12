package m

import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.{Actor, ActorRef}
import akka.stream.FlattenStrategy
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import play.api.libs.json._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object JsonFormats {
}

trait Strategy[-T, U] {
  def apply(o: Source[T])(handler: IntrospectableFlow[U] => Unit)(implicit actorSystem: ActorSystem, materializer: FlowMaterializer): Source[U]
}

class MergeUnorderedStrategy[T] extends Strategy[IntrospectableFlow[T], T] {
  def apply(o: Source[IntrospectableFlow[T]])(handler: IntrospectableFlow[T] => Unit)(implicit actorSystem: ActorSystem, materializer: FlowMaterializer): Source[T] = {
    import MergeUnordered.EnrichedSource
    o.map { flow =>
      handler(flow)
      flow.get
    }.mergeUnordered
  }
}
object Strategy {
  implicit def mergeUnorderedStrategy[T]: Strategy[IntrospectableFlow[T], T] = new MergeUnorderedStrategy[T]
}

object GraphRegistry {
  type Node = IntrospectableFlow[Any]
  type Edge = (IntrospectableFlow[Any], IntrospectableFlow[Any])
  case class RegisterEdge(a: Node, b: Node)

  case class GraphState(nodes: List[Node], edges: List[Edge])
  case class EdgeRegistered(a: Node, b: Node)
  case class NodeRegistered(a: Node)
  case object GraphInitialized
  case object GraphChanged
  case object MarkInitialized
}
class GraphRegistry(listener: ActorRef) extends Actor {
  import GraphRegistry._
  // var notifyChanges = false
  var nodes = List.empty[Node]
  var edges = List.empty[Edge]

  private def maybeRegisterNode(a: Node): Unit = if(! (nodes contains a)) {
    nodes = a :: nodes
    listener ! NodeRegistered(a)
  }

  def receive = {
    case RegisterEdge(a, b) =>
      maybeRegisterNode(a)
      maybeRegisterNode(b)
      edges = (a,b) :: edges
      listener ! EdgeRegistered(a, b)
    case MarkInitialized =>
      listener ! GraphInitialized
  }
}

class IntrospectableFlow[+Out](registry: ActorRef, listener: ActorRef, val nodeName: String, source: Source[Out]) {
  import IntrospectableFlow._
  var edges = List.empty[(IntrospectableFlow[Any], IntrospectableFlow[Any])]

  val id = s"${nodeName}-${nextId}"
  val wrappedSource: Source[Out] = source.map { x =>
    listener ! FlowMessage(id, x)
    x
  }

  def chain[K](newNodeName: String, registerEdge: Boolean = true)(fn: Source[Out] => Source[K]): IntrospectableFlow[K] = {
    val dest = new IntrospectableFlow(registry, listener, newNodeName, fn(wrappedSource))
    if (registerEdge) registry ! GraphRegistry.RegisterEdge(this, dest)
    dest
  }

  def get = wrappedSource

  def groupBy[K](fn: Out => K): IntrospectableFlow[(K, IntrospectableFlow[Out])] = {
    val down = new GroupedIntrospectableFlow(registry, listener, "groupBy", wrappedSource.groupBy(fn).map {
      case (key, flow) =>
        (key, new IntrospectableFlow(registry, listener, s"groupBy:${key}", flow))
    })
    registry ! GraphRegistry.RegisterEdge(this, down)
    down
  }

  def map[K](fn: Out => K): IntrospectableFlow[K] =
    chain("map")(_.map(fn))

  def mergeUnordered[T](implicit mergeUnorderedStrategy: Strategy[Out, T], actorSystem: ActorSystem, materializer: FlowMaterializer): IntrospectableFlow[T] = {
    lazy val mergeJunction: IntrospectableFlow[T] = chain("mergeUnordered", false) { src =>
      mergeUnorderedStrategy(src) { subFlow =>
        registry ! GraphRegistry.RegisterEdge(subFlow, mergeJunction)
      }
    }
    // mergeUnorderedStrategy.boost(this).get.foreach { a =>
    //   
    // }
    mergeJunction
  }

  def foreach(fn: Out => Unit)(implicit ec: ExecutionContext, materializer: FlowMaterializer): Future[Unit] = {
    registry ! GraphRegistry.MarkInitialized
    (chain("foreach") { _.map(identity) }).get.foreach(fn)
  }
}

class GroupedIntrospectableFlow[K, +Out](registry: ActorRef, listener: ActorRef, nodeName: String, source: Source[(K, IntrospectableFlow[Out])]) extends IntrospectableFlow[(K, IntrospectableFlow[Out])](registry, listener, nodeName, source) {
  override def map[U](fn: ((K, IntrospectableFlow[Out])) => U): IntrospectableFlow[U] = {
    lazy val mapNode: IntrospectableFlow[U] = chain("map")(_.map { case (k, f) =>
      registry ! GraphRegistry.RegisterEdge(mapNode, f)
      (k, f)
    }.map(fn))
    mapNode
  }
}


object IntrospectableFlow {
  var id: Int = 0
  case class FlowMessage(nodeId: String, msg: Any)
  def nextId: Int = synchronized {
    id += 1
    id
  }

  def apply[K](listener: ActorRef, source: Source[K])(implicit system: ActorSystem) = {
    val registry = system.actorOf(Props(new GraphRegistry(listener)))
    new IntrospectableFlow(registry, listener, "generator", source)
  }
}

object RunInstance {

  import akka.actor.ActorSystem
  import akka.stream.scaladsl.Source
  import akka.stream.FlowMaterializer
  import scala.util.Random

  case object Shutdown

  def newFlow(listener: ActorRef): ActorRef = {
    implicit val system = ActorSystem("job")
    implicit val materializer = FlowMaterializer()
    import system.dispatcher
    import MergeUnordered.EnrichedSource

    IntrospectableFlow(listener, Source(1 to 1000)).
    // Source(1 to 400).
      groupBy { n => n % 5 == 0 }.
      map {
        case (true, flow) =>
          println(s"Hey, a new value! TRUE")
          flow.
            map { n =>
              // Note, this exception will propagate down the stream to mergeUnordered; ultimately cancelling the stream processing at that junction and causing backpressure on the non-exception branch of this stream.
              // if (n == 50)
              //   throw new RuntimeException("Very exception!!!")
              Thread.sleep(Math.abs(Random.nextInt % 100))
              val p = s"*$n"
              // println(p)
              p
            }
          
        case (false, flow) =>
          flow.
            map { n =>
              Thread.sleep(Math.abs(Random.nextInt % 10))
              val p = s"#$n"
              // println(p)
              p
            }
      }.
      mergeUnordered.
      foreach { d =>
        // d.foreach(identity)
        Thread.sleep(100)
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

class GraphInstance(websocket: ActorRef) extends Actor {
  import JsonFormats._
  import IntrospectableFlow._
  val flow = RunInstance.newFlow(self)

  override def postStop: Unit = {
    println("MahThing is shutting down")
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
    case GraphRegistry.EdgeRegistered(from, to) =>
      websocket ! RoutedMessage(
        "graph.new-edge",
        Json.obj(
          "from" -> from.id,
          "to"   -> to.id)
      )
    // received from IntrospectableFlow
    case FlowMessage(nodeId, value) =>
      websocket ! RoutedMessage("node.message", Json.obj("nodeId" -> nodeId, "value" -> value.toString))
  }
}
