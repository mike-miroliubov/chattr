package org.chats
package dto

import spray.json.DefaultJsonProtocol.*
import spray.json.{DefaultJsonProtocol, JsNumber, JsValue, JsonFormat, RootJsonFormat}

import java.time.Instant

final case class Chat(id: String, fromUserId: String, lastText: String, lastMessageAt: Instant)
final case class Chats(chats: Seq[Chat])
final case class ChatContent(messages: Seq[Message])
final case class Message(id: String, from: String, text: String, receivedAt: Instant)

given instantFormat: JsonFormat[Instant] = new JsonFormat[Instant] {
  override def read(json: JsValue): Instant = json match {
    case JsNumber(x) => Instant.ofEpochMilli(x.toLong)
  }

  override def write(obj: Instant): JsValue = JsNumber(obj.toEpochMilli)
}

given chatFormat: RootJsonFormat[Chat] = jsonFormat4(Chat.apply)
given chatsFormat: RootJsonFormat[Chats] = jsonFormat1(Chats.apply)
given messageFormat: RootJsonFormat[Message] = jsonFormat4(Message.apply)
given chatContentFormat: RootJsonFormat[ChatContent] = jsonFormat1(ChatContent.apply)
