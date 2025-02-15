package org.chats
package service

import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete
import org.chats.service.ClientActor.OutgoingMessage
import org.chats.service.ClientManagerActor.ConnectClient

/**
 * This is the root class of all client actors. It handles creation of new actors as users connect.
 */
class ClientManagerActor(context: ActorContext[ClientManagerActor.Command]) extends AbstractBehavior[ClientManagerActor.Command](context) {
  context.log.info("Messenger Application started")
  override def onMessage(msg: ClientManagerActor.Command): Behavior[ClientManagerActor.Command] = msg match {
    case ConnectClient(userId, queue, replyTo) =>
      val newClient = context.spawn(Behaviors.setup(context => ClientActor(context, queue, userId)), userId)
      replyTo ! newClient
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
  final case class ConnectClient(userId: String, outboundQueue: SourceQueueWithComplete[OutgoingMessage], replyTo: ActorRef[ActorRef[ClientActor.Command]]) extends Command
}