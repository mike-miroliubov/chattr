package org.chats
package repository

import org.chats.model.User

class UserRepository {
  def getUser(username: String, password: String): User = {
    User("id", "kite")
  }
}
