package org.chats
package model

import java.time.{Instant, LocalDateTime}

case class User(id: String, username: String, password: Array[Byte], createdAt: Instant)
case class Session(id: String, token: String, user: User)