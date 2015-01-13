package m

import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl._
import akka.stream.stage.StatefulStage
import akka.util.ByteString
import java.net.InetSocketAddress
import lib.JsonFlow
import play.api.libs.json._
import scala.util._

// object GeoCodeStreamServer extends App {
//   val serverAddress =
//     if (args.length == 3) new InetSocketAddress(args(1), args(2).toInt)
//     else new InetSocketAddress("127.0.0.1", 6000)

//   implicit val system = ActorSystem("Server")
//   import system.dispatcher
//   implicit val materializer = FlowMaterializer()

//   val geoCodeDocument = //JsonFlow.fromByteString.
//     Flow[ByteString].
//       transform { () => new m.lib.LineParser("\n", 65536) }.
//       map(Json.parse).
//       map {
//         case v: JsObject =>
//           Thread.sleep(50)
//           v.asInstanceOf[JsObject] ++ Json.obj("lat" -> (Math.random * 60), "long" -> (Math.random * 90))
//         case o =>
//           println(s"No, I don't know what this is $o")
//           o // I don't know what this... just send it on down ???
//       }.
//       via(JsonFlow.toByteString)

//   val handler = ForeachSink[StreamTcp.IncomingConnection] { conn =>
//     println("Client connected from: " + conn.remoteAddress)
//     conn.
//       handleWith(geoCodeDocument)
//   }

//   val binding = StreamTcp().bind(serverAddress)
//   val materializedServer = binding.connections.to(handler).run()

//   binding.localAddress(materializedServer).onComplete {
//     case Success(address) =>
//       println("Server started, listening on: " + address)
//     case Failure(e) =>
//       println(s"Server could not bind to $serverAddress: ${e.getMessage}")
//       system.shutdown()
//   }

//   // def route[T](v: T) = 'route
//   // def serializeResponse: Flow[Any, ByteString] = null
//   // class HttpRequestParser extends StatefulStage[ByteString, String] {}

//   // StreamTcp().bind(serverAddress).connections. // Source[IncomingConnection]
//   //   buffer(16, OverflowStrategy.error). // drop connections when overloaded
//   //   foreach { conn =>
//   //     conn.handleWith {
//   //       Flow[ByteString].
//   //         transform { () => new HttpRequestParser() }. // parse HTTP document
//   //         map(route).
//   //         via(serializeResponse) // handler could return a response to stream; flatten?
//   //     }
//   //   }

// }
