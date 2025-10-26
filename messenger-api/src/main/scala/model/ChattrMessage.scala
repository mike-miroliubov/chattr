package org.chats
package model

import java.time.LocalDateTime

case class ChattrMessage(
  chatId: String,    // partition key 
  messageId: String, // sort key
  fromUserId: String,
  message: String,
  sentAt: LocalDateTime,
  receivedAt: LocalDateTime,
  deliveredAt: Option[LocalDateTime] = None
)