package org.chats
package repository

import model.Message

import scala.concurrent.Future

trait MessageRepository {
  def getChatMessages(chatId: String, pageSize: Int, seekFromMsgId: Option[String] = None): Future[Seq[Message]]
}
