package org.chats
package util.message

import org.scalactic.Equality
import org.scalactic.TripleEquals.*
import spray.json.*
import spray.json.DefaultJsonProtocol.*

def messageJson(chatId: String, from: String, id: String, text: String): JsObject =
  Map("chatId" -> chatId, "from" -> from, "id" -> id, "text" -> text).toJson.asJsObject

val welcomeMessage = messageJson(
  chatId = "",
  from = "",
  id = "",
  text = "You joined the chat"
)

implicit val messageEquality: Equality[JsObject] = (a: JsObject, b: Any) => {
  b match {
    case other: JsObject => a.fields.removed("id").toJson === other.fields.removed("id").toJson
    case _ => false
  }
}
