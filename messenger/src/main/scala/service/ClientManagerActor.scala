package org.chats
package service

import org.apache.pekko.actor.PoisonPill
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.http.scaladsl.model.ws.Message
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete
import org.chats.service.ClientActor.OutgoingMessage
import org.chats.service.ClientManagerActor.{ConnectClient, ConnectWs}

/**
 * This is the root class of all client actors. It handles creation of new actors as users connect.
 */
class ClientManagerActor(context: ActorContext[ClientManagerActor.Command]) extends AbstractBehavior[ClientManagerActor.Command](context) {
  context.log.info("Messenger Application started")
  override def onMessage(msg: ClientManagerActor.Command): Behavior[ClientManagerActor.Command] = msg match {
    case ConnectClient(userId, queue, replyTo) =>
      val client = context.child(userId)
        .getOrElse(context.spawn(Behaviors.setup(context => ClientActor(context, queue, userId)), userId))
        .unsafeUpcast[ClientActor.Command]
      //val newClient = context.spawn(Behaviors.setup(context => ClientActor(context, queue, userId)), userId)
      replyTo ! client
      this
    case ConnectWs(userId, replyTo) =>
      val client = context.child(s"ws-$userId")
        .getOrElse(context.spawn(Behaviors.setup(context => WsClientInputActor(context)), s"ws-$userId"))
        .unsafeUpcast[Message | PoisonPill]
      //val newClient = context.spawn(Behaviors.setup(context => WsClientInputActor(context)), s"ws-$userId")
      replyTo ! client
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
                                 outboundQueue: SourceQueueWithComplete[OutgoingMessage],
                                 replyTo: ActorRef[ActorRef[ClientActor.Command]]) extends Command
  final case class ConnectWs(userId: String,
                             replyTo: ActorRef[ActorRef[Message | PoisonPill]]) extends Command
}