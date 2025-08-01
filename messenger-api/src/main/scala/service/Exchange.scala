package org.chats
package service

import config.serialization.JsonSerializable
import service.ClientActor.{OutgoingMessage, OutgoingMessageWithAck}

import org.apache.pekko
import org.apache.pekko.actor
import org.apache.pekko.actor.Scheduler
import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.apache.pekko.pattern.RetrySupport
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import org.apache.pekko.util.Timeout

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}
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

  sealed trait Event extends JsonSerializable
  final case class Connected(connection: ActorRef[OutgoingMessage | OutgoingMessageWithAck]) extends Event
  final case class Disconnected(connection: ActorRef[OutgoingMessage | OutgoingMessageWithAck]) extends Event

  final case class State(userId: String, connectedActors: Set[ActorRef[OutgoingMessage | OutgoingMessageWithAck]]) extends JsonSerializable

  /**
   * This actor is implemented as function, rather than as a class. This actor is a persistent one,
   * because Sharded actors are automatically passivated after 2 min (by default) of inactivity.
   * Persistence assures that when the actor is booted up, it gets back it's state (connected actors).
   */
  def apply(userId: String, persistenceId: PersistenceId): Behavior[OutgoingMessage | Exchange.Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[OutgoingMessage | Exchange.Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State(userId, Set.empty),
        commandHandler = commandHandler(context),
        eventHandler = (state, event) => {
          event match
            case Connected(connection) => State(userId, state.connectedActors + connection)
            case Disconnected(connection) => State(userId, state.connectedActors.filterNot(_ == connection))
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
    }
  }

  private def relayMessage(context: ActorContext[OutgoingMessage | Command], state: State, message: OutgoingMessage): Unit = {
    context.log.debug(s"Relaying message ${message.text} to ${state.connectedActors}")

    // TODO: save message to DB here
    if (state.connectedActors.isEmpty) {
      // TODO: send out a push message here
      context.log.debug(s"User ${state.userId} offline")
    } else {
      relayToConnectedActors(context, state, message)
    }
  }

  private def relayToConnectedActors(context: ActorContext[OutgoingMessage | Command], state: State, message: OutgoingMessage): Unit = {
    given ctx: ExecutionContext = context.executionContext

    given scheduler: Scheduler = context.system.classicSystem.scheduler

    val asks = state.connectedActors.map { it =>
      // Send message to connected client actor, waiting for an ack back.
      // If ack is not returned in time, initiate a disconnect.
      // This is done to prevent stale clients that for some reason failed to disconnect gracefully
      // and remain in the persistent state

      import pekko.actor.typed.scaladsl.AskPattern.*
      val ask = RetrySupport.retry(
        attempt = () => it.ask(ref => OutgoingMessageWithAck(message, ref))(3.seconds, context.system.scheduler),
        attempts = 3,
        minBackoff = 0.3.seconds,
        maxBackoff = 3.seconds,
        randomFactor = 0.2
      )

      ask.onComplete {
        case Success(_) =>
        case Failure(_) => context.self ! Disconnect(it)
      }

      ask
    }

    val log = context.log

    // if none of the asks successfully completed, send user a push
    Future.find(asks) { _ => true }.foreach {
      case Some(_) => // TODO: send an ack back that we have delivered
      case None =>
        // TODO: send out a push message here
        log.debug(s"User ${state.userId} offline")
    }
  }
}
