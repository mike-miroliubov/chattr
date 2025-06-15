package org.chats
package service

import service.ClientActor.*

import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.chats.config.{ExchangeShardRegion, GroupShardRegion}
import org.chats.config.serialization.JsonSerializable

/**
 * This is the main user actor. It handles user's incoming and outgoing messages.
 * Each connected user has 1 actor like this, it contains main message routing logic
 */
class ClientActor(context: ActorContext[ClientActor.Command],
                  userId: String,
                  outputActor: ActorRef[OutgoingMessage | ServiceCommand])
                 (using exchangeShardRegion: ExchangeShardRegion, groupShardRegion: GroupShardRegion) extends AbstractBehavior[ClientActor.Command](context) {
  context.log.info("User {} joined", userId)

  // Once the client is instantiated, we connect to an Exchange by sending a message to a ShardRegion.
  // This will either connect to an existing exchange or create a new one.
  exchangeShardRegion ! ShardingEnvelope(userId, Exchange.Connect(context.self))

  override def onMessage(msg: ClientActor.Command): Behavior[ClientActor.Command] = msg match {
    case in: IncomingMessage =>
      context.log.info("Got message: {}", in.text)
      // Because we don't know where the recepient client actor lives we instead send it to an exchange of that user.
      // This will create an empty exchange if that user is not online.
      in.to match {
        case s"g#${_}" => groupShardRegion ! ShardingEnvelope(in.to, OutgoingMessage(in.messageId, in.text, userId))
        case _ => exchangeShardRegion ! ShardingEnvelope(in.to, OutgoingMessage(in.messageId, in.text, userId))
      }

      this
    case out: OutgoingMessage =>
      outputActor ! out
      this
    case OutgoingMessageWithAck(out, replyTo) =>
      outputActor ! out
      replyTo ! Ack(out.messageId)
      this
    case GreetingsMessage =>
      outputActor ! OutgoingMessage("", "You joined the chat", "")
      this
    case ConnectionClosed =>
      context.log.info("Client {} disconnected", userId)
      // disconnect from exchange so that we don't have dangling sessions
      exchangeShardRegion ! ShardingEnvelope(userId, Exchange.Disconnect(context.self))
      outputActor ! ConnectionClosed
      Behaviors.stopped
  }

  override def onSignal: PartialFunction[Signal, Behavior[ClientActor.Command]] = {
    case PostStop =>
      context.log.info("Client {} actor stopped", userId)
      this
  }
}

object ClientActor {
  sealed trait Command extends JsonSerializable
  final case class IncomingMessage(messageId: String, text: String, to: String, from: String) extends Command
  final case class OutgoingMessage(messageId: String, text: String, from: String) extends Command
  // TODO: refactor, remove OutgoingMessage in favor of this OutgoingMessageWithAck
  final case class OutgoingMessageWithAck(message: OutgoingMessage, replyTo: ActorRef[Ack]) extends Command
  case object GreetingsMessage extends Command

  sealed trait ServiceCommand extends Command
  case object ConnectionClosed extends ServiceCommand
  final case class Ack(messageId: String) extends ServiceCommand
}