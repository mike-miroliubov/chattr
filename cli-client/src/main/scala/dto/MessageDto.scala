package org.chats
package dto

import spray.json.DefaultJsonProtocol.*
import spray.json.RootJsonFormat

final case class InputMessageDto(id: String, to: String, text: String)
final case class OutputMessageDto(id: String, from: String, text: String)

given inputMessageFormat: RootJsonFormat[InputMessageDto] = jsonFormat3(InputMessageDto.apply)
given outputMessageFormat: RootJsonFormat[OutputMessageDto] = jsonFormat3(OutputMessageDto.apply)

trait ServiceMessage
case object ConnectionClosed extends ServiceMessage