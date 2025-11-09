package org.chats
package service

import model.{Chat, Message}
import repository.{ChatRepository, MessageRepository}

import scala.concurrent.Future

trait ChatService {
  def getChats(userId: String): Future[Seq[Chat]]
  def getChatMessages(chatId: String): Future[Seq[Message]]
}

class ChatServiceImpl(private val chatRepository: ChatRepository, 
                      private val messageRepository: MessageRepository) extends ChatService {
  override def getChats(userId: String): Future[Seq[Chat]] = chatRepository.findAll(userId)

  override def getChatMessages(chatId: String): Future[Seq[Message]] =
    messageRepository.getChatMessages(chatId)
}
