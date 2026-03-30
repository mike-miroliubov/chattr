package org.chats
package model

import java.time.{Instant, ZonedDateTime}

case class User(id: String, username: String, password: Array[Byte], createdAt: Instant)

case class Session(
  id: String,
  tokenHash: Array[Byte],
  userId: String,
  createdAt: Instant,
  refreshedAt: Option[Instant] = None,
  expiresIn: Int
)