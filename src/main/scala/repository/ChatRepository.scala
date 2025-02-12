package org.chats
package repository

import dto.Chat

trait ChatRepository {
  def getChats: Seq[Chat]
}
