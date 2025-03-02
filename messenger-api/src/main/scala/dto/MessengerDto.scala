package org.chats
package dto

import spray.json.{DefaultJsonProtocol, RootJsonFormat}

final case class InputMessageDto(id: String, to: String, text: String)
final case class OutputMessageDto(id: String, from: String, text: String)

trait MessengerJsonProtocol extends DefaultJsonProtocol {
  implicit val inputMessageFormat: RootJsonFormat[InputMessageDto] = jsonFormat3(InputMessageDto.apply)
  implicit val outputMessageFormat: RootJsonFormat[OutputMessageDto] = jsonFormat3(OutputMessageDto.apply)
}