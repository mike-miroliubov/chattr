package org.chats
package config

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
class AppConfig {
  // These are used to solve circular dependency: system depends on guardian actor, which depends on sharding,
  // which depends on system. They are passed to guardian actor constructor as call by name which allows to call get on them
  // retroactively.
  // This is very hacky. We could maybe solve it differently by having guardian actor not dependent on sharding.
  // For now this works.
  private var groupShardingOpt: Option[GroupShardRegion] = None
  private var userShardingOpt: Option[ExchangeShardRegion] = None

  // This was ActorSystem[Any] in the Pekko Http docs, not sure if its okay to reuse this system for application's actors
  // or we need to build a new one. This somehow works for now.
  implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
    Behaviors.setup(context => ClientManagerActor(context, userShardingOpt.get, groupShardingOpt.get)), "my-system", customizeConfigWithEnvironment())
  val executionContext: ExecutionContextExecutor = system.executionContext

  // Makes sure the ShardRegion is initialized at startup
  val ShardingConfig(userSharding: ExchangeShardRegion, groupSharding: GroupShardRegion) = ShardingConfig()
  groupShardingOpt = Some(groupSharding)
  userShardingOpt = Some(userSharding)

  // TODO: remove, this is a test group
  groupSharding ! ShardingEnvelope("g#test-group", MakeGroup("morpheus", Set("kite", "foo", "neo")))
}
