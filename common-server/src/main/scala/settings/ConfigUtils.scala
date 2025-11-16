package org.chats
package settings

import com.typesafe.config.{Config, ConfigFactory}
import scala.jdk.CollectionConverters.{MapHasAsJava, SetHasAsScala}

def initConfig(resourceName: String, overrides: Map[String, _] = Map()): Config = {
  val defaultConfig = ConfigFactory.load(resourceName)

  val envMap = defaultConfig.entrySet().asScala.map { e =>
    (e.getKey.replaceAll("\\.", "_").replaceAll("-", "").toUpperCase(), e.getKey)
  }.toMap

  val envOverrides = envMap.flatMap { case (envKey, configKey) => sys.env.get(envKey).map((configKey, _)) }

  ConfigFactory.parseMap((envOverrides ++ overrides).asJava).withFallback(defaultConfig)
}
