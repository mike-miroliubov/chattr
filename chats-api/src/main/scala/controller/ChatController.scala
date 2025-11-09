package org.chats
package controller

import dto.{Chat, ChatContent, Chats, Message}
import service.ChatService

import java.time.ZoneOffset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ChatController(chatService: ChatService) {
  def getChats(userId: String): Future[Chats] = chatService.getChats(userId).map { c =>
    Chats(c.map(it => Chat(it.chatId, it.lastMessageFromUserId, it.lastMessage, it.lastMessageSentAt)))
  }
  
  def getChatMessages(chatId: String): Future[ChatContent] = chatService.getChatMessages(chatId).map { messages =>
    ChatContent(messages.map { it => Message(it.messageId, it.fromUserId, it.message, it.receivedAt.toInstant(ZoneOffset.UTC)) })
  }
}
