package org.chats
package service

import org.apache.pekko.actor.PoisonPill
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.ActorRef as UntypedActorRef
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext}
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.chats.service.ClientActor.OutgoingMessage

sealed trait WsProtocol
object Ack extends WsProtocol
object StreamInitialized

class WsClientInputActor(context: ActorContext[Message | PoisonPill],
                         output: UntypedActorRef) extends AbstractBehavior[Message | PoisonPill](context) {

  override def onMessage(msg: Message | PoisonPill): Behavior[Message | PoisonPill] = msg match {
    case m: Message =>
      context.log.info("Got message: {}", m.asTextMessage.getStrictText)
      output ! TextMessage(m.asTextMessage.getStrictText)
      this
    case PoisonPill =>
      context.log.info("Stream closed")
      this
  }
}

class WsClientOutputActor(context: ActorContext[OutgoingMessage]) extends AbstractBehavior[OutgoingMessage](context) {

  override def onMessage(msg: OutgoingMessage): Behavior[OutgoingMessage] = ???
}
