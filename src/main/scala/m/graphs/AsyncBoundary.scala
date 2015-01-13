package m.graphs

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{StreamTcp, Source}
import java.net.InetSocketAddress
import play.api.libs.json._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import m.lib.MergeUnordered.EnrichedSource
import m.IntrospectableFlow
import scala.util.Random
import m.lib.JsonFlow

object AsyncBoundary extends DemoableFlow {
  def generatePerson(id: Int): JsObject = {
    val ip = (1 to 4) map (_ => Math.abs(Random.nextInt % 253)) mkString "."
    Json.obj("id" -> id, "ip" -> ip)
  }

  def flow(listener: ActorRef)(implicit system: ActorSystem): Unit = {
    implicit val materializer = FlowMaterializer()

    val serverAddress = new InetSocketAddress("127.0.0.1", 6000)
    val throughServer = JsonFlow.toByteString.
      via(StreamTcp().outgoingConnection(serverAddress).flow).
      via(JsonFlow.fromByteString)


    IntrospectableFlow(listener, Source(1 to 10000)).
      map(generatePerson).
      via(throughServer).
      foreach(println).
      onComplete { r =>
        println(s"all done ${r}")
        system.shutdown()
      }
  }
}
