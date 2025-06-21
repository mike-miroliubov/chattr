package org.chats
package config

import service.ClientActor.OutgoingMessage
import service.{Exchange, GroupExchange}

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import org.apache.pekko.persistence.typed.PersistenceId

type GroupShardRegion = ActorRef[ShardingEnvelope[OutgoingMessage | GroupExchange.Command]]
type ExchangeShardRegion = ActorRef[ShardingEnvelope[OutgoingMessage | Exchange.Command]]

/**
 * This module initializes all the sharded actors
 */
class ShardingConfig(using system: ActorSystem[_]) {
  val sharding: ClusterSharding = ClusterSharding(system)

  // this enables sharding of Exchanges
  implicit val exchangeShardRegion: ExchangeShardRegion =
    sharding.init(Entity(Exchange.typeKey)(createBehavior = entityContext => Exchange(
      entityContext.entityId,
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))))

  val groupShardRegion: GroupShardRegion =
    sharding.init(Entity(GroupExchange.typeKey)(createBehavior = entityContext => GroupExchange(
      entityContext.entityId,
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))))
}

object ShardingConfig {
  def unapply(arg: ShardingConfig): Some[(ExchangeShardRegion, GroupShardRegion)] = Some((arg.exchangeShardRegion, arg.groupShardRegion))
}

