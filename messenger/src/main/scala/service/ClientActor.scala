package org.chats
package service

import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.stream.scaladsl.SourceQueueWithComplete
import org.chats.service.ClientActor.OutgoingMessage

/**
 * This is the main user actor. It handles user's incoming and outgoing messages.
 * Each connected user has 1 actor like this, it is tied to client's web socket.
 */
class ClientActor(context: ActorContext[ClientActor.Command],
                  userId: String) extends AbstractBehavior[ClientActor.Command](context) {  // outboundQueue: SourceQueueWithComplete[OutgoingMessage],
  context.log.info("User {} joined", userId)
  override def onMessage(msg: ClientActor.Command): Behavior[ClientActor.Command] = msg match {
    case in: ClientActor.IncomingMessage =>
      context.log.info("Got message: {}", in.text)
      //outboundQueue.offer(OutgoingMessage("", s"You said: ${in.text}", ""))
      this
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
  final case class IncomingMessage(messageId: String, text: String, to: String, from: String) extends Command
  final case class OutgoingMessage(messageId: String, text: String, from: String) extends Command
  // final case class Ack(messageId: String) extends Command
}