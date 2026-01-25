package org.chats
package service

import exception.{AuthError, InternalError, LoginFailure}
import model.{Session, User}

import org.chats.repository.UserRepository
import zio.{IO, ZIO}

import java.util.UUID

trait LoginService {
  def login(username: String, password: String): IO[AuthError, Session]
}

class LoginServiceImpl(val userRepository: UserRepository) extends LoginService {
  def login(username: String, password: String): IO[AuthError, Session] = {
    userRepository.getUser(username, password)
      .mapError(e => new InternalError(e))
      .flatMap(
        {
          case Some(u) => ZIO.succeed(Session(
            id = UUID.randomUUID().toString,
            token = "foo",
            user = u
          ))
          case None => ZIO.fail(LoginFailure("Incorrect username or password"))
        }
      )
  }
}