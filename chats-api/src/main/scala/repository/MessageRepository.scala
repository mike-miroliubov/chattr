package org.chats
package repository

import dto.Message

trait MessageRepository {
  def getChatMessages(chatId: String): Seq[Message]
}
