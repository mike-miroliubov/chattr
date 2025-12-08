package org.chats
package repository

import model.Message

import io.getquill.*

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}

class CassandraMessageRepository(val ctx: CassandraAsyncContext[SnakeCase])(using ExecutionContext) extends MessageRepository {
  import ctx.*

  implicit val localDateTimeDecoder: MappedEncoding[Instant, LocalDateTime] = MappedEncoding[Instant, LocalDateTime](LocalDateTime.ofInstant(_, ZoneOffset.UTC))
  implicit val localDateTimeEncoder: MappedEncoding[LocalDateTime, Instant] = MappedEncoding[LocalDateTime, Instant](_.toInstant(ZoneOffset.UTC))

  // Bummer, Quill cannot do > and < out of the box so define an extension to help it
  extension (s: String) {
    inline private def >(other: String): Quoted[Boolean] = quote(infix"$s > $other".as[Boolean])
    inline private def <(other: String): Quoted[Boolean] = quote(infix"$s < $other".as[Boolean])
  }

  override def getChatMessages(chatId: String, pageSize: Int, seekFromMsgId: Option[String] = None): Future[Seq[Message]] = {
    seekFromMsgId match {
      case Some(value) => ctx.run(quote {
        query[Message]
          .filter { m => m.chatId == lift(chatId) && m.messageId < lift(value) }
          .take(lift(pageSize))
      })
      case None => ctx.run(quote {
        query[Message].filter(_.chatId == lift(chatId)).take(lift(pageSize))
      })
    }
  }
}
