package org.chats
package service

import dto.{ApiError, Errors}
import repository.{ChatRepository, MessageRepository}

import org.chats.model.{Chat, Message}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait ChatService {
  def getChats: Future[Seq[Chat]]
  def getChatMessages(chatId: String): Future[Seq[Message]]
}

class ChatServiceImpl(private val chatRepository: ChatRepository, 
                      private val messageRepository: MessageRepository) extends ChatService {
  override def getChats: Future[Seq[Chat]] = chatRepository.findAll

  override def getChatMessages(chatId: String): Future[Seq[Message]] =
    messageRepository.getChatMessages(chatId)
//    chatRepository.getById(chatId)
//      .map(_ => )
//      .toRight(Errors.ObjectNotFoundError)
}
