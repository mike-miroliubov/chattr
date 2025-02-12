package org.chats
package repository

import dto.{Chat, Chats, Message}

import java.time.LocalDateTime

class InMemoryRepository extends ChatRepository with MessageRepository {
  val chats = List(Chat("2fc9a874-271f-45ea-992c-e2f2da483a86", "John Smith", "Hello"))
  val messages = Map("2fc9a874-271f-45ea-992c-e2f2da483a86" -> List(
    Message("fa5af2d2-7627-4839-8c06-b9873fa68116", "alice", "bob", "hi bob", LocalDateTime.now())))
  
  override def getChats: Seq[Chat] = chats

  override def getChatMessages(chatId: String): Seq[Message] = messages(chatId)
}
