package org.chats
package config

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.chats.service.ClientManagerActor
import org.chats.service.GroupExchange.MakeGroup

import scala.concurrent.ExecutionContextExecutor

/**
 * Main config of the application. This is a class to only initialize it in main function.
 * We could also rewrite it to a function or an object and use a local import. Not sure if its a lot better though.
 */
class AppConfig(resourceName: String = "application.conf") {
  // This was ActorSystem[Any] in the Pekko Http docs, not sure if its okay to reuse this system for application's actors
  // or we need to build a new one. This somehow works for now.
  implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
    Behaviors.setup(context => ClientManagerActor(context, userSharding, groupSharding)),
    "my-system",
    customizeConfigWithEnvironment(resourceName)
  )
  val executionContext: ExecutionContextExecutor = system.executionContext

  // Makes sure the ShardRegion is initialized at startup
  val ShardingConfig(userSharding: ExchangeShardRegion, groupSharding: GroupShardRegion) = ShardingConfig()

  // TODO: remove, this is a test group
  groupSharding ! ShardingEnvelope("g#test-group", MakeGroup("morpheus", Set("kite", "foo", "neo")))

  private def customizeConfigWithEnvironment(resourceName: String): Config = {
    ConfigFactory.parseString(
      s"""
      pekko.remote.artery.canonical.port=${sys.env.getOrElse("CLUSTER_PORT", "7354")}
      pekko.persistence.journal.proxy.start-target-journal = ${if sys.env.isDefinedAt("IS_MAIN_INSTANCE") then "on" else "off"}
      pekko.persistence.snapshot-store.proxy.start-target-snapshot-store = ${if sys.env.isDefinedAt("IS_MAIN_INSTANCE") then "on" else "off"}
      """).withFallback(ConfigFactory.load(resourceName))
  }
}
