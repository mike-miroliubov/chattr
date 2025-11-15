package org.chats
package config

import pureconfig._

case class Settings(server: ServerSettings) derives ConfigReader
case class ServerSettings(host: String, port: Int)
