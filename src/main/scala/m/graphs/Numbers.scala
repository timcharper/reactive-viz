package m.graphs

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Source
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import m.lib.MergeUnordered.EnrichedSource
import m.IntrospectableFlow
object Numbers extends DemoableFlow {
  def isPrime(n: Int): Boolean = {
    if (n <= 2) return false
    Thread.sleep(15) // artificially slow it down
    val ceiling = math.sqrt(n.toDouble).toInt
    (2 until ceiling) forall (x => n % x != 0)
  }

  // Tim's notes: simple, fold, concurrent, grouped; input=15000 to show buffering
  // text-scale-adjust
  def flow(listener: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val materializer = FlowMaterializer()

    // Super simple:
    val out = IntrospectableFlow(listener, Source(1 to 10000)).
      groupBy(i => i % 5 == 0).
      map {  // Source[(groupByValue, Source[flow])]
        case (true, multsOf5) =>
          multsOf5.
            map { i =>
              i
            }
        case (false, others) =>
          others.
            map { i =>
              Thread.sleep(100)
            }
      }.
      mergeUnordered.
      foreach(println).
      onComplete { r =>
        println(s"Result : ${r}")
        system.shutdown()
      }
  }



































    // FOLD:
    // IntrospectableFlow(listener, Source(1 to 1000)).
    //   filter(isPrime).
    //   fold(0)(_ + _).
    //   onComplete { result =>
    //     println(s"Result is $result")
    //     system.shutdown()
    //   }






    // CONCURRENT:
    // IntrospectableFlow(listener, Source(1 to 5000)).
    //   grouped(100).
    //   mapAsync { values => Future { values.filter(isPrime)}}.
    //   mapConcat(identity).
    //   fold(0)(_ + _).
    //   onComplete { n =>
    //     println(s"result is $n")
    //     system.shutdown()
    //   }






    
    // GROUPED: (no concurrent)
    // IntrospectableFlow(listener, Source(1 to 1000)).
    //   groupBy(isPrime).
    //   map {
    //     case (true, primes) =>
    //       primes.
    //         fold(0)(_ + _).
    //         map { result => s"Prime sum is ${result}" }
    //     case (false, nonPrimes) =>
    //       nonPrimes.
    //         fold(0)(_ + _).
    //         map { result => s"nonPrime sum is ${result}" }
    //   }.
    //   mapAsync(identity).
    //   foreach(println).
    //   onComplete { _ =>
    //     system.shutdown()
    //   }
  // }
}
