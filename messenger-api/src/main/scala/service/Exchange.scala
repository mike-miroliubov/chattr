package org.chats
package service

import config.serialization.JsonSerializable
import service.ClientActor.{OutgoingMessage, OutgoingMessageWithAck}

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import org.apache.pekko.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Failure, Success}


/**
 * Exchange is a sharded actor that represents a mediator that connects client actors.
 * It is created either by an owner client joining or by other client trying to send it a message.
 * After it is created, an owner client need to connect to it using a Connect message, it will add it to the exchange state.
 * This will also natively support multiple sessions for one user (e.g. multiple connected devices).
 */
object Exchange {
  val typeKey: EntityTypeKey[OutgoingMessage | Exchange.Command] = EntityTypeKey("Exchange")

  sealed trait Command extends JsonSerializable
  final case class Connect(connection: ActorRef[OutgoingMessage | OutgoingMessageWithAck]) extends Command
  final case class Disconnect(connection: ActorRef[OutgoingMessage | OutgoingMessageWithAck]) extends Command
  private final case class Delivered(messageId: String) extends Command

  sealed trait Event extends JsonSerializable
  final case class Connected(connection: ActorRef[OutgoingMessage | OutgoingMessageWithAck]) extends Event
  final case class Disconnected(connection: ActorRef[OutgoingMessage | OutgoingMessageWithAck]) extends Event

  final case class State(connectedActors: Set[ActorRef[OutgoingMessage | OutgoingMessageWithAck]]) extends JsonSerializable

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
        commandHandler = commandHandler(context),
        eventHandler = (state, event) => {
          event match
            case Connected(connection) => State(state.connectedActors + connection)
            case Disconnected(connection) => State(state.connectedActors.filterNot(_ == connection))
        }
      )
      .withRetention(
        RetentionCriteria.snapshotEvery(numberOfEvents = 5, keepNSnapshots = 1)
          .withDeleteEventsOnSnapshot
      )
    }

  private def commandHandler(context: ActorContext[OutgoingMessage | Command])
                            (state: State, cmd: OutgoingMessage | Command): Effect[Event, State] = {
    cmd match {
      case Connect(connection) => Effect.persist(Connected(connection))
      case Disconnect(connection) => Effect.persist(Disconnected(connection))
      case message: OutgoingMessage =>
        relayMessage(context, state, message)
        Effect.none
      case _: Delivered => Effect.none // TODO: we should reply to the caller here that we delivered
    }
  }

  private def relayMessage(context: ActorContext[OutgoingMessage | Command], state: State, message: OutgoingMessage): Unit = {
    context.log.debug(s"Relaying message ${message.text} to ${state.connectedActors}")

    implicit val timeout: Timeout = 3.seconds

    state.connectedActors.foreach { it =>
      // Send message to connected client actor, waiting for an ack back.
      // If ack is not returned in time, initiate a disconnect.
      // This is done to prevent stale clients that for some reason failed to disconnect gracefully
      // and remain in the persistent state
      context.ask(it, ref => OutgoingMessageWithAck(message, ref)) {
        case Failure(exception) =>
          // TODO: we actually want to do a few retries here
          context.log.debug(s"Connected actor $it did not reply, removing it")
          Disconnect(it)
        case Success(a) => Delivered(message.messageId) // ack was received, nothing to do here
      }
    }
  }
}
