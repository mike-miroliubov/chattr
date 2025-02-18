package org.chats
package service

import service.ClientActor.OutgoingMessage
import service.ClientManagerActor.ConnectClient

import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import org.apache.pekko.routing.{ActorRefRoutee, AddRoutee, BroadcastGroup}

/**
 * This is the root class of all client actors. It handles creation of new actors as users connect.
 */
class ClientManagerActor(context: ActorContext[ClientManagerActor.Command]) extends AbstractBehavior[ClientManagerActor.Command](context) {
  context.log.info("Messenger Application started")

  // using a classic router here because they have a optimization to route concurrently, which typed routers don't
  // https://doc.akka.io/libraries/akka-core/current/typed/routers.html#routers-and-performance
  // https://doc.akka.io/libraries/akka-core/current/routing.html#how-routing-is-designed-within-akka
  private val globalRouter = context.actorOf(BroadcastGroup(List()).props(), "global-router")

  override def onMessage(msg: ClientManagerActor.Command): Behavior[ClientManagerActor.Command] = msg match {
    case ConnectClient(userId, output, replyTo) =>
      val actor = context.child(userId)
        .getOrElse(context.spawn(Behaviors.setup(context => ClientActor(context, userId, output, globalRouter)), userId))
        .unsafeUpcast[ClientActor.Command]

      globalRouter ! AddRoutee(ActorRefRoutee(actor.toClassic))
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