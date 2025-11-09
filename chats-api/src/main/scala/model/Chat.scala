package org.chats
package model

case class Chat(
    userId: String,
    chatId: String,
    lastMessageFromUserId: String,
    lastMessageSentAt: java.time.Instant,
    lastMessageId: String,
    lastMessage: String,
)
