package org.chats
package service

import model.{Session, User}

import zio.{UIO, ZIO}

import java.time.Instant
import java.util.UUID

trait SessionService {
  def createSession(user: User): UIO[(Session, String)]
}

object SessionServiceImpl {
  private val EXPIRES_IN = 7 * 24 * 60 * 60 // 7 days in seconds
}

class SessionServiceImpl extends SessionService {
  override def createSession(user: User): UIO[(Session, String)] = {
    ZIO.succeed(Session(
      id = UUID.randomUUID().toString,
      tokenHash = Array(123.toByte),
      userId = user.id,
      createdAt = Instant.now(),
      expiresIn = SessionServiceImpl.EXPIRES_IN
    ), "fooToken")
  }
}

