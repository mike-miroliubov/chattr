package org.chats
package repository

import model.Message

import io.getquill.*

import scala.concurrent.{ExecutionContext, Future}

class CassandraMessageRepository(val ctx: CassandraAsyncContext[SnakeCase])(using ExecutionContext) extends MessageRepository {
  import ctx._

  override def getChatMessages(chatId: String): Future[Seq[Message]] = ctx.run(quote {
    query[Message].filter(_.chatId == lift(chatId))
  })
}
