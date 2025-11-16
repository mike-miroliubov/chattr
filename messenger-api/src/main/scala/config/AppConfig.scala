package org.chats
package config

import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import org.chats.repository.{MessageRepository, MessageRepositoryImpl}
import org.chats.service.ClientManagerActor
import org.chats.service.GroupExchange.MakeGroup
import org.chats.settings.initConfig
import pureconfig.ConfigSource

import scala.jdk.CollectionConverters.{MapHasAsJava, SetHasAsScala}
import scala.concurrent.ExecutionContextExecutor

/**
 * Main config of the application. This is a class to only initialize it in main function.
 * We could also rewrite it to a function or an object and use a local import. Not sure if its a lot better though.
 */
class AppConfig(configResourceName: String = "application.conf", overrides: Map[String, _] = Map()) {
  private val config: Config = initConfig(configResourceName, overrides)
  val settings: Settings = ConfigSource.fromConfig(config).loadOrThrow[Settings]
  // This was ActorSystem[Any] in the Pekko Http docs, not sure if its okay to reuse this system for application's actors
  // or we need to build a new one. This somehow works for now.
  implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
    Behaviors.setup(context => ClientManagerActor(context, userSharding, groupSharding, messageRepository)),
    "my-system",
    config
  )
  val executionContext: ExecutionContextExecutor = system.executionContext

  // Makes sure the ShardRegion is initialized at startup
  val ShardingConfig(userSharding: ExchangeShardRegion, groupSharding: GroupShardRegion) = ShardingConfig()

  // TODO: remove, this is a test group
  groupSharding ! ShardingEnvelope("g#test-group", MakeGroup("morpheus", Set("kite", "foo", "neo")))

  val cassandraSession: CassandraSession = CassandraSessionRegistry.get(system).sessionFor(CassandraSessionSettings())
  val messageRepository: MessageRepository = MessageRepositoryImpl(cassandraSession, system)
}