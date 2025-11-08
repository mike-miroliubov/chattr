package org.chats
package service

import service.ClientActor.OutgoingMessage
import service.ClientManagerActor.ConnectClient

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.chats.config.{ExchangeShardRegion, GroupShardRegion}
import org.chats.repository.MessageRepository

/**
 * This is the root class of all client actors. It handles creation of new actors as users connect.
 */
class ClientManagerActor(context: ActorContext[ClientManagerActor.Command],
                         // These are passed as call-by-name to solve circular dependency: the system depends on
                         // guardian actor, which depends on sharding, which depends on system.
                         // Passing as call by name which allows to get them retroactively.
                         // This is very hacky. We could maybe solve it differently by having guardian
                         // actor not dependent on sharding.
                         // For now this works.
                         exchangeShardRegionSupplier: => ExchangeShardRegion,
                         groupShardRegionSupplier: => GroupShardRegion,
                         messageRepositorySupplier: => MessageRepository)
                         extends AbstractBehavior[ClientManagerActor.Command](context) {
  context.log.info("Messenger Application started")

  override def onMessage(msg: ClientManagerActor.Command): Behavior[ClientManagerActor.Command] = msg match {
    case ConnectClient(userId, output, replyTo) =>
      given groupShardRegion: GroupShardRegion = groupShardRegionSupplier
      given userShardingRegion: ExchangeShardRegion = exchangeShardRegionSupplier
      given messageRepository: MessageRepository = messageRepositorySupplier
      val actor = context.child(userId)
        .getOrElse(context.spawn(Behaviors.setup(context => ClientActor(context, userId, output)), s"client-$userId"))
        .unsafeUpcast[ClientActor.Command]

      replyTo ! actor  // for the ask pattern we should return the newly created actor to the caller
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
                                 output: ActorRef[OutgoingMessage | ClientActor.ServiceCommand],
                                 replyTo: ActorRef[ActorRef[ClientActor.Command]]) extends Command
}