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

import java.util.UUID
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
        relayMessage2(context, state, message)
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

  private def relayMessage2(context: ActorContext[OutgoingMessage | Command], state: State, message: OutgoingMessage): Unit = {
    context.log.debug(s"Relaying message ${message.text} to ${state.connectedActors}")

    state.connectedActors.foreach { it =>
      // Send message to connected client actor, waiting for an ack back.
      // If ack is not returned in time, initiate a disconnect.
      // This is done to prevent stale clients that for some reason failed to disconnect gracefully
      // and remain in the persistent state.
      // We use a new actor to perform message delivery so that it can encapsulate the retries.
      // TODO: do no spawn a new actor every time, reuse 1!
      val deliveryActor = context.spawn(DeliveryActor(it, 3), s"delivery-${UUID.randomUUID()}")
      given timeout: Timeout = 30.seconds // its a long timeout because we will receive a response anyway
      context.ask[DeliveryActor.Send, DeliveryActor.Ack | DeliveryActor.Nack](deliveryActor, ref => DeliveryActor.Send(message = message, replyTo = ref)) {
        case Failure(_) => Disconnect(it)
        case Success(DeliveryActor.Ack(messageId, _)) => Delivered(message.messageId) // ack was received, nothing to do here
        case Success(DeliveryActor.Nack(unreachableActor, _)) => Disconnect(unreachableActor)
      }
    }
  }
}

/**
 * Delivery actor ensures delivery, waiting for an Ack. It will also encapsulate retry logic
 */
object DeliveryActor {
  def apply(recipient: ActorRef[OutgoingMessage | OutgoingMessageWithAck], maxAttempts: Int): Behavior[Command] = {
    Behaviors.receive { (context, message) =>
      message match {
        case Send(m, replyTo, attempt) =>
          implicit val timeout: Timeout = 3.seconds

          // maybe simplify with an ask that returns a future
          context.ask(recipient, ref => OutgoingMessageWithAck(m, ref)) {
            case Failure(exception) =>
              if (attempt == maxAttempts) {
                context.log.debug(s"Connected actor $recipient did not reply after $maxAttempts attempts, removing it")
                Nack(recipient, Some(replyTo))
              } else {
                Retry(m, replyTo, attempt)
              }
            case Success(a) => Ack(m.messageId, Some(replyTo))
          }

          Behaviors.same
        case Ack(messageId, Some(replyTo)) =>
          replyTo ! Ack(messageId)
          Behaviors.stopped
        case Nack(recipient, Some(replyTo)) =>
          replyTo ! Nack(recipient)
          Behaviors.stopped
        case Retry(m, replyTo, attempt) =>
          val waitTime = LazyList.iterate(0.3.seconds.toMillis){ exponentialBackoff(_, 2) }.drop(attempt - 1).head.millis
          context.log.debug(s"Waiting $waitTime until next attempt #${attempt + 1}")
          context.scheduleOnce(waitTime, context.self, Send(m, replyTo, attempt + 1))
          Behaviors.same
      }
    }
  }

  private def exponentialBackoff(sleepMs: Long, multiplier: Long): Long = sleepMs * multiplier

  sealed trait Command
  final case class Send(message: OutgoingMessage, replyTo: ActorRef[Ack | Nack], attempt: Int = 1) extends Command
  final case class Nack(recipient: ActorRef[OutgoingMessage | OutgoingMessageWithAck],
                        replyTo: Option[ActorRef[Ack | Nack]] = None) extends Command
  final case class Ack(messageId: String,
                       replyTo: Option[ActorRef[Ack | Nack]] = None) extends Command
  final case class Retry(message: OutgoingMessage, replyTo: ActorRef[Ack | Nack], attempt: Int = 1) extends Command
}
