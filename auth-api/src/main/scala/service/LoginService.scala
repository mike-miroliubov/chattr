package org.chats
package service

import exception.{AuthError, LoginFailure}
import model.{Session, User}
import repository.UserRepository

import zio.{IO, ZIO}

import java.util.UUID

trait LoginService {
  def login(username: String, password: String): IO[AuthError, Session]
}

class LoginServiceImpl(val userRepository: UserRepository) extends LoginService {
  def login(username: String, password: String): IO[AuthError, Session] = {
    userRepository.getUser(username)
      .flatMap(
        {
          case Some(u) if passwordMatches(password, u.password) =>
            ZIO.succeed(Session(
              id = UUID.randomUUID().toString,
              token = "foo",
              user = u
            ))
          case _ => ZIO.fail(LoginFailure("Incorrect username or password"))
        }
      )
  }

  private def passwordMatches(password: String, expectedHash: Array[Byte]): Boolean = {
    val (salt, hash) = expectedHash.splitAt(16)
    val providedPasswordHash = hashPassword(password, salt)
    providedPasswordHash.sameElements(hash)
  }
}