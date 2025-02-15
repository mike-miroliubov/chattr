package org.chats
package service

import dto.{ApiError, Chat, Errors, Message}
import repository.{ChatRepository, MessageRepository}

trait ChatService {
  def getChats: Seq[Chat]
  def getChatMessages(chatId: String): Either[ApiError, Seq[Message]]
}

class ChatServiceImpl(private val chatRepository: ChatRepository, 
                      private val messageRepository: MessageRepository) extends ChatService {
  override def getChats: Seq[Chat] = chatRepository.findAll

  override def getChatMessages(chatId: String): Either[ApiError, Seq[Message]] =
    chatRepository.getById(chatId)
      .map(_ => messageRepository.getChatMessages(chatId))
      .toRight(Errors.ObjectNotFoundError)
}
