package org.chats
package service

import exception.{AuthError, LoginFailure}
import model.{Session, User}
import repository.UserRepository

import zio.{IO, ZIO}

trait LoginService {
  def login(username: String, password: String): IO[AuthError, String]
}

class LoginServiceImpl(val userRepository: UserRepository, val sessionService: SessionService) extends LoginService {
  def login(username: String, password: String): IO[AuthError, String] = {
    userRepository.getUser(username)
      .flatMap(
        {
          case Some(u) if passwordMatches(password, u.password) => sessionService.createSession(u).map(_._2)
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