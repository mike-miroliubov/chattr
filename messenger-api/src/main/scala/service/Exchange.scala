package org.chats
package service

import service.ClientActor.OutgoingMessage

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.scaladsl.{Entity, EntityTypeKey}


/**
 * Exchange is a sharded actor that represents a mediator that connects client actors.
 * It is created either by an owner client joining or by other client trying to send it a message.
 * After it is created, an owner client need to connect to it using a Connect message, it will add it to the exchange state.
 * This will also natively support multiple sessions for one user (e.g. multiple connected devices).
 */
object Exchange {
  val typeKey: EntityTypeKey[OutgoingMessage | Exchange.Command] = EntityTypeKey("Exchange")

  // this enables sharding of Exchanges
  val shardRegion: ActorRef[ShardingEnvelope[OutgoingMessage | Exchange.Command]] =
    sharding.init(Entity(typeKey)(createBehavior = entityContext => Exchange(entityContext.entityId)))

  sealed trait Command
  final case class Connect(connection: ActorRef[OutgoingMessage]) extends Command
  final case class Disconnect(connection: ActorRef[OutgoingMessage]) extends Command

  /**
   * This actor is implemented as function, rather than as a class. This actor, perhaps need to be converted to a persistent one
   */
  def apply(userId: String): Behavior[OutgoingMessage | Exchange.Command] = {
    def inner(connectedActors: List[ActorRef[OutgoingMessage]]): Behavior[OutgoingMessage | Exchange.Command] = Behaviors.receiveMessage {
        case Connect(connection) => inner(connection :: connectedActors)
        case Disconnect(connection) => inner(connectedActors.filterNot { _ == connection })
        case message: OutgoingMessage =>
          connectedActors.foreach { _ ! message }
          Behaviors.same
      }

    inner(Nil)
  }
}
