package org.chats
package config

import pureconfig.*

case class Settings(chattr: ChattrSettings) derives ConfigReader
case class ChattrSettings(server: ServerSettings)
case class ServerSettings(
  chatsApiUrl: String,
  messengerApiUrl: String
)
