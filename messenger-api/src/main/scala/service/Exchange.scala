package org.chats
package service

import service.ClientActor.OutgoingMessage

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.scaladsl.{Entity, EntityTypeKey}
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}


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
    sharding.init(Entity(typeKey)(createBehavior = entityContext => Exchange(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))))

  sealed trait Command
  final case class Connect(connection: ActorRef[OutgoingMessage]) extends Command
  final case class Disconnect(connection: ActorRef[OutgoingMessage]) extends Command

  sealed trait Event
  final case class Connected(connection: ActorRef[OutgoingMessage]) extends Event
  final case class Disconnected(connection: ActorRef[OutgoingMessage]) extends Event

  final case class State(connectedActors: Set[ActorRef[OutgoingMessage]])

  /**
   * This actor is implemented as function, rather than as a class. This actor is a persistent one,
   * because Sharded actors are automatically passivated after 2 min (by default) of inactivity.
   * Persistence assures that when the actor is booted up, it gets back it's state (connected actors).
   */
  def apply(userId: String, persistenceId: PersistenceId): Behavior[OutgoingMessage | Exchange.Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[OutgoingMessage | Exchange.Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State(Set.empty),
        commandHandler = (state, cmd) => {
          cmd match {
            case Connect(connection) => Effect.persist(Connected(connection))
            case Disconnect(connection) => Effect.persist(Disconnected(connection))
            case message: OutgoingMessage =>
              context.log.debug(s"Relaying message ${message.text} to ${state.connectedActors}")
              state.connectedActors.foreach {
                _ ! message
              }
              Effect.none
          }
        },
        eventHandler = (state, event) => {
          event match
            case Connected(connection) => State(state.connectedActors + connection)
            case Disconnected(connection) => State(state.connectedActors.filterNot(_ == connection))
        }
      )
    }
}
