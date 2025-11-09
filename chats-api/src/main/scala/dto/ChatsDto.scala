package org.chats
package dto

import spray.json.{DefaultJsonProtocol, JsNumber, JsString, JsValue, JsonFormat, RootJsonFormat}

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime}


final case class ChatUser(id: String, username: String, isOnline: Boolean)
final case class Message(id: String, from: String, text: String, receivedAt: Instant)
final case class Chat(id: String, fromUserId: String, lastText: String, lastMessageAt: Instant)
final case class Chats(chats: Seq[Chat])
final case class ChatContent(messages: Seq[Message])

trait ChattrJsonProtocol extends DefaultJsonProtocol {
  implicit val localDateTimeFormat: JsonFormat[LocalDateTime] = new JsonFormat[LocalDateTime] {
    private val ISO_DATE_TIME = DateTimeFormatter.ISO_DATE_TIME

    def write(x: LocalDateTime): JsString = JsString(ISO_DATE_TIME.format(x))

    def read(value: JsValue): LocalDateTime = value match {
      case JsString(x) => LocalDateTime.parse(x, ISO_DATE_TIME)
      case x => throw new RuntimeException(s"Unexpected type ${x.getClass.getName} when trying to parse LocalDateTime")
    }
  }

  implicit val instantFormat: JsonFormat[Instant] = new JsonFormat[Instant] {
    override def read(json: JsValue): Instant = json match {
      case JsNumber(x) => Instant.ofEpochMilli(x.toLong)
    }

    override def write(obj: Instant): JsValue = JsNumber(obj.toEpochMilli)
  }
  
  implicit val chatFormat: RootJsonFormat[Chat] = jsonFormat4(Chat.apply)
  implicit val chatsFormat: RootJsonFormat[Chats] = jsonFormat1(Chats.apply)
  implicit val messageFormat: RootJsonFormat[Message] = jsonFormat4(Message.apply)
  implicit val chatContentFormat: RootJsonFormat[ChatContent] = jsonFormat1(ChatContent.apply)
  implicit val apiErrorFormat: RootJsonFormat[ApiError] = jsonFormat2(ApiError.apply)
}