package m.lib

import akka.stream.io.Framing
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import play.api.libs.json._


object JsonFlow {
  val toByteString: Flow[JsValue, ByteString, Unit] =
    Flow[JsValue].
      map { jsValue => ByteString(jsValue.toString + "\n") }

  val fromByteString: Flow[ByteString, JsValue, Unit] =
    Flow[ByteString].
      via(Framing.delimiter(ByteString("\r\n"), maximumFrameLength = 65536, allowTruncation = false)).
      map { l => Json.parse(l.utf8String) }

  def withSerialization(flow: Flow[ByteString, ByteString, Unit]): Flow[JsValue, JsValue, Unit] = {
    toByteString via flow via fromByteString
  }
}
