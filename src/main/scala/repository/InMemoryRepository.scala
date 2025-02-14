package org.chats
package repository

import dto.{Chat, Message}

import org.apache.pekko.event.slf4j.Logger
import org.chats.repository.InMemoryRepository.logger

import java.time.LocalDateTime

class InMemoryRepository extends ChatRepository with MessageRepository {
  logger.info("Creating repository")

  val chats = Map(
    "2fc9a874-271f-45ea-992c-e2f2da483a86" -> Chat("2fc9a874-271f-45ea-992c-e2f2da483a86", "John Smith", "Hello"))

  val messages = Map(
    "2fc9a874-271f-45ea-992c-e2f2da483a86" -> List(
      Message("fa5af2d2-7627-4839-8c06-b9873fa68116", "alice", "hi bob", LocalDateTime.now())))

  override def findAll: Seq[Chat] = chats.values.toSeq

  override def getById(chatId: String): Option[Chat] = chats.get(chatId)

  override def getChatMessages(chatId: String): Seq[Message] = messages(chatId)
}

object InMemoryRepository {
  private final val logger = Logger(InMemoryRepository.getClass, InMemoryRepository.getClass.toString)
}