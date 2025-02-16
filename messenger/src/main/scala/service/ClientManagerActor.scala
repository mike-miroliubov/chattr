package org.chats
package service

import service.ClientActor.{IncomingMessage, OutgoingMessage}
import service.ClientManagerActor.{ConnectClient, ConnectWsInput, ConnectWsOutput}

import org.apache.pekko.actor.PoisonPill
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.apache.pekko.http.scaladsl.model.ws.Message

/**
 * This is the root class of all client actors. It handles creation of new actors as users connect.
 */
class ClientManagerActor(context: ActorContext[ClientManagerActor.Command]) extends AbstractBehavior[ClientManagerActor.Command](context) {
  context.log.info("Messenger Application started")

  override def onMessage(msg: ClientManagerActor.Command): Behavior[ClientManagerActor.Command] = msg match {
    case ConnectClient(userId, output, replyTo) =>
      val actor = context.child(userId)
        .getOrElse(context.spawn(Behaviors.setup(context => ClientActor(context, userId, output)), userId))
        .unsafeUpcast[ClientActor.Command]
      replyTo ! actor
      this
      
    case ConnectWsInput(userId, output, replyTo) =>
      val actor = context.child(s"ws-in-$userId")
        .getOrElse(context.spawn(Behaviors.setup(context => WsClientInputActor(context, output)), s"ws-in-$userId"))
        .unsafeUpcast[Message | PoisonPill]
      replyTo ! actor
      this
      
    case ConnectWsOutput(userId, output, replyTo) =>
      val actor = context.child(s"ws-out-$userId") match
        case Some(value) =>
          val existingActor = value.unsafeUpcast[OutgoingMessage | WsClientOutputActor.Command]
          existingActor ! WsClientOutputActor.SetOutput(output)
          existingActor
        case None => context.spawn(Behaviors.setup(context => WsClientOutputActor(context, output)), s"ws-out-$userId")
      replyTo ! actor
      this
  }

  override def onSignal: PartialFunction[Signal, Behavior[ClientManagerActor.Command]] = {
    case PostStop =>
      context.log.info("Messenger Application stopped")
      this
  }
}

object ClientManagerActor {
  sealed trait Command
  final case class ConnectClient(userId: String,
                                 output: ActorRef[OutgoingMessage],
                                 replyTo: ActorRef[ActorRef[ClientActor.Command]]) extends Command
  final case class ConnectWsInput(userId: String,
                                  output: ActorRef[IncomingMessage],
                                  replyTo: ActorRef[ActorRef[Message | PoisonPill]]) extends Command
  final case class ConnectWsOutput(userId: String,
                                   output: ActorRef[Message],
                                   replyTo: ActorRef[ActorRef[OutgoingMessage]]) extends Command
}