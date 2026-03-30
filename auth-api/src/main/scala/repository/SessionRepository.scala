package org.chats
package repository

import model.Session

import io.getquill.*
import io.getquill.jdbczio.Quill
import zio.{IO, UIO, ZIO}

import java.sql.SQLException

trait SessionRepository {
  def create(session: Session): UIO[_]
  def getSessionByTokenHash(tokenHash: Array[Byte]): UIO[Option[Session]]
}

class SessionRepositoryImpl(quill: Quill.Postgres[SnakeCase]) extends SessionRepository {
  import quill.*
  override def create(session: model.Session): UIO[_] = run(
    quote { query[model.Session].insertValue(lift(session)) }
  ).orDie

  override def getSessionByTokenHash(tokenHash: Array[Byte]): UIO[Option[model.Session]] = run(
    quote { query[model.Session].filter(_.tokenHash == lift(tokenHash)).take(1) }
  ).map(_.headOption).orDie
}
