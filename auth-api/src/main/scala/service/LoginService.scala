package org.chats
package service

import exception.AuthError
import model.{Session, User}

import zio.{IO, ZIO}

import java.util.UUID

trait LoginService {
  def login(username: String, password: String): IO[AuthError, Session]
}

class LoginServiceImpl extends LoginService {
  def login(username: String, password: String): IO[AuthError, Session] = {
    ZIO.succeed(Session(
      id = UUID.randomUUID().toString,
      token = "foo",
      user = User("foo", "bar")
    ))
  }
}