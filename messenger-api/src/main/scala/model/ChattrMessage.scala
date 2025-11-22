package org.chats
package model

import org.chats.config.serialization.JsonSerializable

import java.time.LocalDateTime

case class ChattrMessage(
  chatId: String,    // partition key 
  messageId: String, // sort key
  clientMessageId: String,
  fromUserId: String,
  message: String,
  sentAt: LocalDateTime,
  receivedAt: LocalDateTime,
  deliveredAt: Option[LocalDateTime] = None
) extends JsonSerializable {
  def to: String = {
    chatId match {
      case s"g#${_}" => chatId
      case s"${this.fromUserId}#${toUserId}" => toUserId
      case s"${toUserId}#${this.fromUserId}" => toUserId
    }
  }
}