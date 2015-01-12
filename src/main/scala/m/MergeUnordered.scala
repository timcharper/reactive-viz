package m

import akka.actor.{ ActorSystem, Props, ActorRef }
import akka.stream.FlowMaterializer
import akka.stream.actor.{RequestStrategy, ActorPublisherMessage, ActorSubscriberMessage, ActorSubscriber, MaxInFlightRequestStrategy, ActorPublisher }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.stage._
import scala.annotation.tailrec
import scala.collection.mutable.Queue
import scala.util.{Failure, Success}

object MergeUnordered {
  implicit class EnrichedSource[Out](source: Source[Source[Out]]) {
    def mergeUnordered(implicit system: ActorSystem, materializer: FlowMaterializer): Source[Out] =
      apply(source)
  }


  case object ConsumerCompleted
  case object NudgeConsumer
  case class NewConsumer(a: ActorRef)
  case class ConsumerError(t: Throwable)
  case class ConsumerCountKnown(n: Int)

  private class StreamSubscriber(publisher: ActorRef, publisherBlockingStrategy: RequestStrategy) extends ActorSubscriber {
    import ActorSubscriberMessage.{OnNext, OnError, OnComplete}

    override val requestStrategy = publisherBlockingStrategy

    override def preStart: Unit = {
      publisher ! NewConsumer(self)
    }

    def receive = {
      case msg: OnNext =>
        publisher ! msg

      case OnError(e) =>
        publisher ! ConsumerError(e)

      case OnComplete =>
        publisher ! ConsumerCompleted

      case NudgeConsumer =>
        // simply processing a message will trigger a call to request(requestStrategy.requestDemand(remainingRequested))
    }
  }

  class MergedStreamPublisher[K](maxQueueLength: Int, onInit: (ActorRef, RequestStrategy) => Unit) extends ActorPublisher[K] {
    import ActorSubscriberMessage.OnNext
    import ActorPublisherMessage.{Cancel, Request}
    val queue = Queue.empty[K]
    var consumers = List.empty[ActorRef]
    var consumersCompleted: Int = 0
    var consumerCount: Option[Int] = None

    override def preStart: Unit = 
      onInit(
        self,
        new MaxInFlightRequestStrategy(max = maxQueueLength) {
          override def inFlightInternally: Int = {
            queue.length // Presently, the implementation of this is to return the value of the private variable `len`, and is thread-safe; I am not sure what concurrency safety guarantees are made my Scala so don't know if we can always count on this
          }
        }
      )

    def receive = {
      case OnNext(data) if isActive => // we want to dead-letter messages that are pushed from the input sources, but not received / propagated here.
        queue.enqueue(data.asInstanceOf[K])
        doPush()

      case Request(v) =>
        if (isActive) doPush()

      case NewConsumer(a) =>
        consumers = a :: consumers
        doPush()

      case ConsumerCountKnown(n) =>
        consumerCount = Some(n)
        maybeComplete()

      case ConsumerCompleted =>
        consumersCompleted += 1
        maybeComplete()

      case Failure(e)=>
        onError(e)

      case ConsumerError(e) =>
        onError(e)

      case Cancel =>
        context.stop(self)
    }

    @inline private def maybeComplete(): Unit = 
      if ((Some(consumersCompleted) == consumerCount) && queue.isEmpty) {
        onComplete() // if some error occurred upstream, then the streams should not be completed and we will not arrive at this state.
        context.stop(self)
      }


    @tailrec private final def pushIterator(): Unit =
      if (!queue.isEmpty && totalDemand > 0) {
        onNext(queue.dequeue)
        pushIterator()
      }

    @inline private def doPush(): Unit = {
      pushIterator()
      if (totalDemand > 0) consumers.foreach { _ ! NudgeConsumer }
      maybeComplete()
    }
  }

  def apply[K](streams: Source[Source[K]], maxQueueLength: Int = 20)(implicit system: ActorSystem, materializer: FlowMaterializer): Source[K] = {
    def boot(publisher: ActorRef, publisherBlockingStrategy: RequestStrategy): Unit = {
      import system.dispatcher
      streams.
        map { m =>
          m.runWith(Sink(Props(new StreamSubscriber(publisher, publisherBlockingStrategy))))
        }.
        fold(0) { (a, _) => a + 1 }.
        onComplete {
          case Success(n) =>
            publisher ! ConsumerCountKnown(n)
          case Failure(e: Throwable) =>
            publisher ! Failure(e)
        }
    }

    Source[K](Props(new MergedStreamPublisher(maxQueueLength, boot)))
  }
}
