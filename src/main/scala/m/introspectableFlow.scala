package m

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.FlowShape
import akka.stream.Graph
import akka.stream.Materializer
import akka.stream.SinkShape
import akka.stream.scaladsl.{FlattenStrategy, Flow, Source}
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

trait Strategy[-T, U, M] {
  def apply[M2](o: Source[T, M2])(handler: IntrospectableFlow[U, M] => Unit)(implicit actorSystem: ActorSystem, materializer: Materializer): Source[U, M2]
}

// class MergeUnorderedStrategy[-T, +M] extends Strategy[IntrospectableFlow[T, M], T, M] {
//   def apply(o: Source[IntrospectableFlow[T, M], M])(handler: IntrospectableFlow[T, M] => Unit)(implicit actorSystem: ActorSystem, materializer: Materializer): Source[T, M] = {
//     import MergeUnordered.EnrichedSource
//     o.map { flow =>
//       handler(flow)
//       flow.get
//     }.mergeUnordered
//   }
// }
// object Strategy {
//   implicit def mergeUnorderedStrategy[T, M]: Strategy[IntrospectableFlow[T, M], T, M] = new MergeUnorderedStrategy[T, M]
// }

class IntrospectableFlattenStrategy[T, M](val wrapped: FlattenStrategy[Source[T, M], T]) extends Strategy[IntrospectableFlow[T, M], T, M] {
  def apply[M2](o: Source[IntrospectableFlow[T, M], M2])(handler: IntrospectableFlow[T, M] => Unit)(implicit actorSystem: ActorSystem, materializer: Materializer): Source[T, M2] = {
    o.map { flow =>
      handler(flow)
      flow.get
    }.flatten(wrapped)
  }
}

object GraphRegistry {
  case class EdgeProperties(
    subStream: Option[Boolean] = None,
    label: Option[String] = None)

  type Node = IntrospectableFlow[Any, _]
  type Edge = (IntrospectableFlow[Any, _], IntrospectableFlow[Any, _])
  case class RegisterEdge(a: Node, b: Node, properties: EdgeProperties = EdgeProperties())

  case class GraphState(nodes: List[Node], edges: List[Edge])
  case class EdgeRegistered(a: Node, b: Node, properties: EdgeProperties)
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
    case RegisterEdge(a, b, props) =>
      maybeRegisterNode(a)
      maybeRegisterNode(b)
      edges = (a,b) :: edges
      listener ! EdgeRegistered(a, b, props)
    case MarkInitialized =>
      listener ! GraphInitialized
  }
}


trait LinkStrategy {
  def linkChild(registry: ActorRef, self: GraphRegistry.Node, downstream: GraphRegistry.Node): Unit
}
class DefaultLinkStrategy extends LinkStrategy {
  def linkChild(registry: ActorRef, self: GraphRegistry.Node, downstream: GraphRegistry.Node): Unit =
    registry ! GraphRegistry.RegisterEdge(self, downstream)
}
class GroupEdgeLinkStrategy(groupNode: GraphRegistry.Node, keyValue: Any) extends LinkStrategy {
  // here, we ignore the "self" argument, which is a temporary IntrospectableFlow object we'd prefer to not show the user
  def linkChild(registry: ActorRef, self: GraphRegistry.Node, downstream: GraphRegistry.Node): Unit =
    registry ! GraphRegistry.RegisterEdge(groupNode, downstream, GraphRegistry.EdgeProperties(subStream = Some(true), label = Some(keyValue.toString)))
}

class IntrospectableFlow[+Out, M](registry: ActorRef, listener: ActorRef, val nodeName: String, source: Source[Out, M], linkStrategy: LinkStrategy = new DefaultLinkStrategy) {
  import IntrospectableFlow._
  val id = s"${nodeName}-${nextId}"
  val wrappedSource: Source[Out, M] = source.map { x =>
    listener ! FlowMessage(id, x)
    x
  }

  // creates new node, registering edge downstream to it
  protected def chain[K](newNodeName: String)(fn: Source[Out, M] => Source[K, M]): IntrospectableFlow[K, M] = {
    val dest = new IntrospectableFlow(registry, listener, newNodeName, fn(wrappedSource))
    linkStrategy.linkChild(registry, this, dest)
    dest
  }

  def get = wrappedSource

  // I actually don't want the streams to be linked to the groupBy node, even though the groupBy node creates them; I want that to happen in the subsequent map.
  def groupBy[K](fn: Out => K): IntrospectableFlow[(K, IntrospectableFlow[Out, Unit]), M] = {
    lazy val groupByNode: IntrospectableFlow[(K, IntrospectableFlow[Out, Unit]), M] = chain("groupBy") {
      _.groupBy(fn).map {
        case (key, flow) =>
          (key, new IntrospectableFlow(registry, listener, s"groupBy:${key}", flow, new GroupEdgeLinkStrategy(groupByNode, key)))
      }
    }
    groupByNode
  }

  def map[K](fn: Out => K): IntrospectableFlow[K, M] =
    chain("map")(_.map(fn))

  // creates new node, registering upstream edges to it
  // def mergeUnordered[T](implicit mergeUnorderedStrategy: Strategy[Out, T, M], actorSystem: ActorSystem, materializer: Materializer): IntrospectableFlow[T, M] = {
  //   lazy val mergeJunction: IntrospectableFlow[T, M] = chain("mergeUnordered") { src =>
  //     mergeUnorderedStrategy(src) { subFlow =>
  //       registry ! GraphRegistry.RegisterEdge(subFlow, mergeJunction, GraphRegistry.EdgeProperties(subStream = Some(true)))
  //     }
  //   }
  //   mergeJunction
  // }

  def flatten[T](flattenStrategy: Strategy[Out, T, M])(implicit actorSystem: ActorSystem, materializer: Materializer): IntrospectableFlow[T, M] = {
    lazy val flattenJunction: IntrospectableFlow[T, M] = chain("flatten") { src =>
      flattenStrategy(src) { f =>
        registry ! GraphRegistry.RegisterEdge(f, flattenJunction, GraphRegistry.EdgeProperties(subStream = Some(true)))
      }
    }
    flattenJunction
  }

  def mapAsync[U](parallelism: Int)(fn: Out => Future[U]): IntrospectableFlow[U, M] =
    chain("mapAsync")(_.mapAsync(parallelism)(fn))

  def mapAsyncUnordered[U](parallelism: Int)(fn: Out => Future[U]): IntrospectableFlow[U, M] =
    chain("mapAsyncUnordered")(_.mapAsyncUnordered(parallelism: Int)(fn))


  def grouped(n: Int): IntrospectableFlow[immutable.Seq[Out], M] =
    chain("grouped")(_.grouped(n))


  def groupedWithin(n: Int, d: FiniteDuration): IntrospectableFlow[immutable.Seq[Out], M] =
    chain("groupedWithin")(_.groupedWithin(n, d))


  def runFold[U](zero: U)(f: (U, Out) => U)(implicit materializer: Materializer): Future[U] = {
    registry ! GraphRegistry.MarkInitialized // this is a lie.
    (chain("runFold") { _.map(identity) }).get.runFold(zero)(f)
  }

  def runWith[Mat2](sink: Graph[SinkShape[Out], Mat2])(implicit materializer: Materializer): Mat2 = {
    registry ! GraphRegistry.MarkInitialized // this is a lie.
    (chain(s"runWith(${sink})") { _.map(identity) }).get.runWith(sink)
  }

  def mapConcat[T](fn: Out => immutable.Seq[T]): IntrospectableFlow[T, M] =
    chain("mapConcat")(_.mapConcat(fn))

  def filter(fn: Out => Boolean): IntrospectableFlow[Out, M] =
    chain("filter")(_.filter(fn))

  def via[T, Mat2](flow: Graph[FlowShape[Out, T], Mat2]): IntrospectableFlow[T, M] = {
    chain("via")(_.via(flow))
  }

}

object IntrospectableFlow {
  object Implicits {
    implicit def i[T, M](f: FlattenStrategy[Source[T, M], T]): IntrospectableFlattenStrategy[T, M] = {
      new IntrospectableFlattenStrategy(f)
    }
  }
  var id: Int = 0
  case class FlowMessage(nodeId: String, msg: Any)
  def nextId: Int = synchronized {
    id += 1
    id
  }

  def apply[K, M](listener: ActorRef, source: Source[K, M])(implicit system: ActorSystem) = {
    val registry = system.actorOf(Props(new GraphRegistry(listener)))
    new IntrospectableFlow(registry, listener, "generator", source)
  }
}

