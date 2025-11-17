package org.chats
package settings

import com.typesafe.config.{Config, ConfigFactory, ConfigValueType}
import scala.jdk.CollectionConverters.given

import scala.jdk.CollectionConverters.{MapHasAsJava, SetHasAsScala}

def initConfig(resourceName: String, overrides: Map[String, _] = Map()): Config = {
  val defaultConfig = ConfigFactory.load(resourceName)

  val envMap = defaultConfig.entrySet().asScala.map { e =>
    (e.getKey.replaceAll("\\.", "_").replaceAll("-", "").toUpperCase(), e.getKey)
  }.toMap

  val envOverrides = envMap.flatMap {
    case (envKey, configKey) =>
      val defaultValue = defaultConfig.getValue(configKey)
      // Based on default value type we need to parse the env variable value
      sys.env.get(envKey).map { it =>
        defaultValue.valueType() match {
          // If it's a list config setting, split the env variable as a comma-separated list
          case ConfigValueType.LIST =>
            (configKey, it.split(",").toBuffer.asJava)
          case _ =>
            (configKey, it)
        }
      }
  }

  ConfigFactory.parseMap((envOverrides ++ overrides).asJava).withFallback(defaultConfig)
}
