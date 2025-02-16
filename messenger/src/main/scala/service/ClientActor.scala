package org.chats
package service

import service.ClientActor.OutgoingMessage
import service.WsClientOutputActor.ConnectionClosed

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}

/**
 * This is the main user actor. It handles user's incoming and outgoing messages.
 * Each connected user has 1 actor like this, it contains main message routing logic
 */
class ClientActor(context: ActorContext[ClientActor.Command],
                  userId: String,
                  wsClientOutputActor: ActorRef[OutgoingMessage | WsClientOutputActor.Command]) extends AbstractBehavior[ClientActor.Command](context) {  // outboundQueue: SourceQueueWithComplete[OutgoingMessage],
  context.log.info("User {} joined", userId)
  override def onMessage(msg: ClientActor.Command): Behavior[ClientActor.Command] = msg match {
    case in: ClientActor.IncomingMessage =>
      context.log.info("Got message: {}", in.text)

      // for now we'll just route it to ws output actor to echo back to the user. Later we will send it to other users' actors
      wsClientOutputActor ! OutgoingMessage("", s"You said: ${in.text}", "")
      this
    case out: ClientActor.OutgoingMessage =>
      wsClientOutputActor ! out
      this
    case ClientActor.GreetingsMessage =>
      wsClientOutputActor ! OutgoingMessage("", "You joined the chat", "")
      this
    case ClientActor.ConnectionClosed => 
      wsClientOutputActor ! ConnectionClosed
      Behaviors.stopped   
  }

  override def onSignal: PartialFunction[Signal, Behavior[ClientActor.Command]] = {
    case PostStop =>
      context.log.info("Client {} disconnected", userId)
      this
  }
}

object ClientActor {
  sealed trait Command
  // TODO: maybe abb replyTo: ActorRef[Ack]
  // final case class Ack(messageId: String) extends Command
  final case class IncomingMessage(messageId: String, text: String, to: String, from: String) extends Command
  final case class OutgoingMessage(messageId: String, text: String, from: String) extends Command
  case object GreetingsMessage extends Command
  case object ConnectionClosed extends Command
}