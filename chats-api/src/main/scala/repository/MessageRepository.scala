package org.chats
package repository

import model.Message

import scala.concurrent.Future

trait MessageRepository {
  def getChatMessages(chatId: String): Future[Seq[Message]]
}
