package org.chats
package service

import org.apache.pekko.actor.typed.{Behavior, PostStop, Signal}
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

/**
 * This is the main user actor. It handles user's incoming and outgoing messages.
 * Each connected user has 1 actor like this, it is tied to client's web socket.
 */
class ClientActor(context: ActorContext[Any], userId: String) extends AbstractBehavior[Any](context) {
  context.log.info("User {} joined", userId)
  override def onMessage(msg: Any): Behavior[Any] = Behaviors.unhandled

  override def onSignal: PartialFunction[Signal, Behavior[Any]] = {
    case PostStop =>
      context.log.info("Client {} disconnected", userId)
      this
  }
}
