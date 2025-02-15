package org.chats
package repository

import dto.Chat

trait ChatRepository {
  def findAll: Seq[Chat]
  def getById(chatId: String): Option[Chat]
}
