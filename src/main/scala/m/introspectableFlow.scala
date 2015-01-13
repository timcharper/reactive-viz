package m

import akka.actor.{ActorSystem, Props, Actor, ActorRef}
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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

trait LinkingStrategyThing {
  // it's like the graph should be able to chain up and down;
  // each flow should optionally be able to link itself to it's parent.
  def announceNode(node: GraphRegistry.Node)
}

class ParentLinkingThing(val parent: GraphRegistry.Node, registry: ActorRef) {
  def announceNode(node: GraphRegistry.Node): Unit =
    registry ! GraphRegistry.RegisterEdge(parent, node)
}

object NullLinkingThing {
  def announceNode(node: GraphRegistry.Node): Unit = ()
}

class IntrospectableFlow[+Out](registry: ActorRef, listener: ActorRef, val nodeName: String, source: Source[Out]) {
  import IntrospectableFlow._
  var edges = List.empty[(IntrospectableFlow[Any], IntrospectableFlow[Any])]

  val id = s"${nodeName}-${nextId}"
  val wrappedSource: Source[Out] = source.map { x =>
    listener ! FlowMessage(id, x)
    x
  }

  protected def chain[K](newNodeName: String, registerEdge: Boolean = true)(fn: Source[Out] => Source[K]): IntrospectableFlow[K] = {
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

  def bottleneck[K](delayMs: Int): IntrospectableFlow[Out] =
    chain(s"bottleneck(${delayMs}ms)")(_.map { x => Thread.sleep(delayMs); x })

  def mergeUnordered[T](implicit mergeUnorderedStrategy: Strategy[Out, T], actorSystem: ActorSystem, materializer: FlowMaterializer): IntrospectableFlow[T] = {
    lazy val mergeJunction: IntrospectableFlow[T] = chain("mergeUnordered", false) { src =>
      mergeUnorderedStrategy(src) { subFlow =>
        registry ! GraphRegistry.RegisterEdge(subFlow, mergeJunction)
      }
    }
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

