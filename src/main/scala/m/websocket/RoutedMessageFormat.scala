package m.websocket

import play.api.libs.json._

object RoutedMessageFormat {
  import play.api.libs.functional.syntax._
  implicit val routedMessageReads: Reads[RoutedMessage] = (
    (JsPath)(0).read[String] and
    (JsPath)(1).read[JsValue]
  )(RoutedMessage.apply _)

  implicit val routedMessageWrites = Writes[RoutedMessage] { (message) =>
    Json.arr(message.topic, message.payload)
  }
}
