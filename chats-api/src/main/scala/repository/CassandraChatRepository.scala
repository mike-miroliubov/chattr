package org.chats
package repository

import model.Chat

import io.getquill.*

import scala.concurrent.{ExecutionContext, Future}

class CassandraChatRepository(val ctx: CassandraAsyncContext[SnakeCase])(using ExecutionContext) extends ChatRepository{
  import ctx._
  
  def findAll(userId: String): Future[Seq[Chat]] = ctx.run(quote {
    querySchema[Chat]("inbox").filter(_.userId == lift(userId))
  })
}
