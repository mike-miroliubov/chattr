package org.chats
package config

import zio.Config
import zio.config.magnolia.deriveConfig

case class Settings(db: DBSettings)
case class DBSettings(url: String, username: String, password: String, schema: String)

object SettingsConfig {
  given config: Config[Settings] = deriveConfig[Settings]
}
