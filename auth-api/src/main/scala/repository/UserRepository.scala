package org.chats
package repository

import io.getquill.*
import io.getquill.jdbczio.Quill
import org.chats.model.User
import zio.ZIO

import java.sql.SQLException

trait UserRepository {
  def getUser(username: String, password: String): ZIO[Any, SQLException, Option[User]]
  def create(user: User): ZIO[Any, SQLException, _]
}

class UserRepositoryImpl(quill: Quill.Postgres[SnakeCase]) extends UserRepository {
  import quill._

  def getUser(username: String, password: String): ZIO[Any, SQLException, Option[User]] = {
    run(quote { querySchema[User]("chat_user")
      .filter(_.username == lift(username)).take(1) })
      .map(_.headOption)
  }

  def create(user: User): ZIO[Any, SQLException, _] = {
    run(quote {querySchema[User]("chat_user").insertValue(lift(user))})
  }
}
