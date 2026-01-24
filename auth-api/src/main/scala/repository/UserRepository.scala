package org.chats
package repository

import org.chats.model.User
import zio.ZIO

import java.sql.SQLException

trait UserRepository {
  def getUser(username: String, password: String): ZIO[Any, SQLException, User]
}

class UserRepositoryImpl extends UserRepository {
  def getUser(username: String, password: String): ZIO[Any, SQLException, User] = {
    ZIO.succeed(User("id", "kite"))
  }
}
