package org.chats
package dto

import org.chats.model.ChattrMessage
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.time.{LocalDateTime, ZoneOffset}

final case class InputMessageDto(id: String, to: String, text: String) {
  def toModel(from: String): ChattrMessage = {
    val chatId = to match {
      case s"g#${_}" => to
      case _ => Seq(to, from).sorted.mkString("#")
    }

    ChattrMessage(
      chatId,
      id,
      from,
      text,
      LocalDateTime.now(ZoneOffset.UTC), // TODO: replace with the timestamp, sent by the client
      LocalDateTime.now(ZoneOffset.UTC)
    )
  }
}

final case class OutputMessageDto(id: String, from: String, text: String)

object OutputMessageDto {
  def apply(model: ChattrMessage): OutputMessageDto = OutputMessageDto(model.messageId, model.fromUserId, model.message)
}

trait MessengerJsonProtocol extends DefaultJsonProtocol {
  implicit val inputMessageFormat: RootJsonFormat[InputMessageDto] = jsonFormat3(InputMessageDto.apply)
  implicit val outputMessageFormat: RootJsonFormat[OutputMessageDto] = jsonFormat3(OutputMessageDto.apply)
}