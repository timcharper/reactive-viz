package m.lib

import akka.stream.scaladsl.Flow
import akka.util.ByteString
import play.api.libs.json._


object JsonFlow {
  val toByteString: Flow[JsValue, ByteString] =
    Flow[JsValue].
      map { jsValue => ByteString(jsValue.toString + "\n") }

  val fromByteString: Flow[ByteString, JsValue] =
    Flow[ByteString].
      transform( () => new LineParser("\n", 65536 )).
      map(Json.parse)

  def withSerialization(flow: Flow[ByteString, ByteString]): Flow[JsValue, JsValue] = {
    toByteString via flow via fromByteString
  }
}
