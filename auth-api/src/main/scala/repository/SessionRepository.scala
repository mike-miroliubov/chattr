package org.chats
package repository

import model.Session

import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{UIO, ZIO}

import java.sql.SQLException
import java.time.{Instant, LocalDateTime, ZoneOffset}

trait SessionRepository {
  def create(session: Session): UIO[_]
  def getSessionByTokenHash(tokenHash: Array[Byte]): UIO[Option[Session]]
}

class SessionRepositoryImpl(quill: Quill.Postgres[SnakeCase]) extends SessionRepository {
  // exclude default instant encoder and decoder to override them using LocalDateTime
  import quill.{instantEncoder as _, instantDecoder as _, *}

  given MappedEncoding[Instant, LocalDateTime] = MappedEncoding(i => LocalDateTime.ofInstant(i, ZoneOffset.UTC))
  given MappedEncoding[LocalDateTime, Instant] = MappedEncoding(_.toInstant(ZoneOffset.UTC))

  override def create(session: model.Session): UIO[_] = run(
    quote {
      query[model.Session].insertValue(lift(session))
    }
  ).orDie

  override def getSessionByTokenHash(tokenHash: Array[Byte]): UIO[Option[model.Session]] = run(
    quote {
      query[model.Session].filter(_.tokenHash == lift(tokenHash)).take(1)
    }
  ).map(_.headOption).orDie
}
