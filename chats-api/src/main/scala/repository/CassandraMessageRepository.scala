package org.chats
package repository

import model.Message

import io.getquill.*

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}

class CassandraMessageRepository(val ctx: CassandraAsyncContext[SnakeCase])(using ExecutionContext) extends MessageRepository {
  import ctx.*

  implicit val localDateTimeDecoder: MappedEncoding[Instant, LocalDateTime] = MappedEncoding[Instant, LocalDateTime](LocalDateTime.ofInstant(_, ZoneOffset.UTC))

  override def getChatMessages(chatId: String): Future[Seq[Message]] = ctx.run(quote {
    query[Message].filter(_.chatId == lift(chatId))
  })
}
