package org.chats
package repository

import model.Chat

import io.getquill.*

import scala.concurrent.{ExecutionContext, Future}


object Queries {
  val findAllQuery: Quoted[EntityQuery[Chat]] = quote {
    query[Chat]
  }
}

class CassandraChatRepository(val ctx: CassandraAsyncContext[SnakeCase])(using ExecutionContext) extends ChatRepository{
  import ctx._
  
  def findAll: Future[Seq[Chat]] = ctx.run(quote {
    querySchema[Chat]("inbox")
  })

  def getById(chatId: String): Option[Chat] = ???
}
