package org.chats
package config

import pureconfig._
import org.chats.settings.ServerSettings

case class Settings(server: ServerSettings) derives ConfigReader
