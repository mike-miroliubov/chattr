package org.chats
package repository

import java.time.{Instant, LocalDateTime, ZoneOffset}
import io.getquill.*
import io.getquill.jdbczio.Quill
import org.chats.model.User
import zio.ZIO

import java.sql.SQLException

given localDateTimeDecoder: MappedEncoding[Instant, LocalDateTime] = MappedEncoding[Instant, LocalDateTime](LocalDateTime.ofInstant(_, ZoneOffset.UTC))
given localDateTimeEncoder: MappedEncoding[LocalDateTime, Instant] = MappedEncoding[LocalDateTime, Instant](_.toInstant(ZoneOffset.UTC))

trait UserRepository {
  def getUser(username: String): ZIO[Any, Nothing, Option[User]]
  def create(user: User): ZIO[Any, SQLException, _]
}

class UserRepositoryImpl(quill: Quill.Postgres[SnakeCase]) extends UserRepository {
  import quill._

  def getUser(username: String): ZIO[Any, Nothing, Option[User]] = {
    run(quote { querySchema[User]("chat_user")
      .filter(_.username == lift(username)).take(1) })
      .map(_.headOption).orDie
  }

  def create(user: User): ZIO[Any, Nothing, _] = {
    run(quote {querySchema[User]("chat_user").insertValue(lift(user))}).orDie
  }
}
