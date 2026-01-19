package org.chats
package model

case class User(id: String, name: String)
case class Session(id: String, token: String, user: User)