package org.chats
package model

import java.time.LocalDateTime

case class User(id: String, username: String, password: Array[Byte], createdAt: LocalDateTime)
case class Session(id: String, token: String, user: User)