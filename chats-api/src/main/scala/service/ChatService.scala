package org.chats
package service

import dto.{ApiError, Errors, Message}
import repository.{ChatRepository, MessageRepository}

import org.chats.model.Chat

import scala.concurrent.Future

trait ChatService {
  def getChats: Future[Seq[Chat]]
  def getChatMessages(chatId: String): Either[ApiError, Seq[Message]]
}

class ChatServiceImpl(private val chatRepository: ChatRepository, 
                      private val messageRepository: MessageRepository) extends ChatService {
  override def getChats: Future[Seq[Chat]] = chatRepository.findAll

  override def getChatMessages(chatId: String): Either[ApiError, Seq[Message]] =
    chatRepository.getById(chatId)
      .map(_ => messageRepository.getChatMessages(chatId))
      .toRight(Errors.ObjectNotFoundError)
}
