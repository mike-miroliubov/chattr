package org.chats
package dto

import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}
import spray.json.DefaultJsonProtocol.jsonFormat3

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


final case class ChatUser(id: String, username: String, isOnline: Boolean)
final case class Message(id: String, from: String, to: String, text: String, createdAt: LocalDateTime)
final case class Chat(id: String, name: String, lastText: String)
final case class Chats(chats: Seq[Chat])
final case class ChatContent(messages: Seq[Message])

trait WhisperJsonProtocol extends DefaultJsonProtocol {
  implicit val localDateTimeFormat: JsonFormat[LocalDateTime] = new JsonFormat[LocalDateTime] {
    private val ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME

    def write(x: LocalDateTime): JsString = JsString(ISO_DATE_TIME.format(x))

    def read(value: JsValue): LocalDateTime = value match {
      case JsString(x) => LocalDateTime.parse(x, ISO_DATE_TIME)
      case x => throw new RuntimeException(s"Unexpected type ${x.getClass.getName} when trying to parse LocalDateTime")
    }
  }
  
  implicit val chatFormat: RootJsonFormat[Chat] = jsonFormat3(Chat.apply)
  implicit val chatsFormat: RootJsonFormat[Chats] = jsonFormat1(Chats.apply)
  implicit val messageFormat: RootJsonFormat[Message] = jsonFormat5(Message.apply)
  implicit val chatContentFormat: RootJsonFormat[ChatContent] = jsonFormat1(ChatContent.apply)
}