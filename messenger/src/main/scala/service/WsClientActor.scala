package org.chats
package service

import service.ClientActor.{IncomingMessage, OutgoingMessage}

import org.apache.pekko.actor.PoisonPill
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import org.apache.pekko.stream.scaladsl.Sink

sealed trait WsProtocol
object Ack extends WsProtocol
object StreamInitialized

/**
 * This actor is part of web layer, it handles raw messages from the websocket
 */
class WsClientInputActor(context: ActorContext[Message | PoisonPill],
                         output: ActorRef[Message]) extends AbstractBehavior[Message | PoisonPill](context) {

  override def onMessage(msg: Message | PoisonPill): Behavior[Message | PoisonPill] = msg match {
    case tm: TextMessage =>
      context.log.info("Got message: {}", tm.asTextMessage.getStrictText)
      output ! TextMessage(tm.asTextMessage.getStrictText)
      this
    case bm: BinaryMessage =>
      // ignore binary messages but drain content to avoid the stream being clogged
      bm.dataStream.runWith(Sink.ignore)
      this
    case PoisonPill =>
      context.log.info("Stream closed")
      this
  }
}

/**
 * This actor will also be part of web layer, once implemented. It will transfer raw messages to websocket
 */
class WsClientOutputActor(context: ActorContext[OutgoingMessage]) extends AbstractBehavior[OutgoingMessage](context) {

  override def onMessage(msg: OutgoingMessage): Behavior[OutgoingMessage] = ???
}
