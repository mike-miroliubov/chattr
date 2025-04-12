package org.chats

import config.{ExchangeShardRegion, GroupShardRegion, ShardingConfig}
import service.ClientManagerActor

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

class IntegrationTestConfig {
  private var groupShardingOpt: Option[GroupShardRegion] = None
  private var userShardingOpt: Option[ExchangeShardRegion] = None

  // This was ActorSystem[Any] in the Pekko Http docs, not sure if its okay to reuse this system for application's actors
  // or we need to build a new one. This somehow works for now.
  implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
    Behaviors.setup(context => ClientManagerActor(context, userShardingOpt.get, groupShardingOpt.get)), "my-system", ConfigFactory.load("application-test.conf"))

  // Makes sure the ShardRegion is initialized at startup
  val ShardingConfig(userSharding: ExchangeShardRegion, groupSharding: GroupShardRegion) = ShardingConfig()
  groupShardingOpt = Some(groupSharding)
  userShardingOpt = Some(userSharding)

  // TODO: remove, this is a test group
  //groupSharding ! ShardingEnvelope("g#test-group", MakeGroup("morpheus", Set("kite", "foo", "neo")))
}
