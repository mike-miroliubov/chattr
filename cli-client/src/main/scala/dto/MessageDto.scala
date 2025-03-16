package org.chats
package dto

import spray.json.DefaultJsonProtocol.*
import spray.json.RootJsonFormat

final case class OutputMessageDto(id: String, to: String, text: String)
final case class InputMessageDto(id: String, from: String, text: String)

given inputMessageFormat: RootJsonFormat[OutputMessageDto] = jsonFormat3(OutputMessageDto.apply)
given outputMessageFormat: RootJsonFormat[InputMessageDto] = jsonFormat3(InputMessageDto.apply)

trait ServiceMessage
case object ConnectionClosed extends ServiceMessage