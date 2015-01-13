package m

import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.stream.scaladsl._
import java.net.InetSocketAddress
import lib.JsonFlow
import play.api.libs.json._
import scala.util._

object GeoCodeStreamServer extends App {
  val serverAddress =
    if (args.length == 3) new InetSocketAddress(args(1), args(2).toInt)
    else new InetSocketAddress("127.0.0.1", 6000)

  implicit val system = ActorSystem("Server")
  import system.dispatcher
  implicit val materializer = FlowMaterializer()

  val geoCodeDocument = JsonFlow.fromByteString.
    map {
      case v: JsObject =>
        // apply sophisticated geocoding algorithm
        v.asInstanceOf[JsObject] ++ Json.obj("lat" -> (Math.random * 60), "long" -> (Math.random * 90))
      case o =>
        o // I don't know what this... just send it on down ???
    }.
    via(JsonFlow.toByteString)


  val handler = ForeachSink[StreamTcp.IncomingConnection] { conn =>
    println("Client connected from: " + conn.remoteAddress)
    conn.
      handleWith(geoCodeDocument)
  }

  val binding = StreamTcp().bind(serverAddress)
  val materializedServer = binding.connections.to(handler).run()

  binding.localAddress(materializedServer).onComplete {
    case Success(address) =>
      println("Server started, listening on: " + address)
    case Failure(e) =>
      println(s"Server could not bind to $serverAddress: ${e.getMessage}")
      system.shutdown()
  }
}
