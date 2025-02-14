package org.chats
package service

import dto.{Chat, Message}

import org.chats.repository.{ChatRepository, MessageRepository}

trait ChatService {
  def getChats: Seq[Chat]
  def getChatMessages(chatId: String): Option[Seq[Message]]
}

class ChatServiceImpl(private val chatRepository: ChatRepository, 
                      private val messageRepository: MessageRepository) extends ChatService {
  override def getChats: Seq[Chat] = chatRepository.findAll

  override def getChatMessages(chatId: String): Option[Seq[Message]] = chatRepository.getById(chatId)
    .map(_ => messageRepository.getChatMessages(chatId))
}
