package org.chats
package repository

import model.Chat

import scala.concurrent.Future

trait ChatRepository {
  def findAll(userId: String): Future[Seq[Chat]]
}
