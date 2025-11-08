package org.chats
package repository

import model.Chat

import scala.concurrent.Future

trait ChatRepository {
  def findAll: Future[Seq[Chat]]
  def getById(chatId: String): Option[Chat]
}
