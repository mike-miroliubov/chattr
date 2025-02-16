package org.chats
package service

import service.ClientActor.{IncomingMessage, OutgoingMessage}
import service.WsClientInputActor.{Command, ConnectionClosed}

import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import org.apache.pekko.stream.scaladsl.Sink

sealed trait WsProtocol
object Ack extends WsProtocol
object StreamInitialized

/**
 * This actor is part of web layer, it handles raw messages from the websocket
 */
class WsClientInputActor(context: ActorContext[Message | WsClientInputActor.Command],
                         client: ActorRef[ClientActor.Command]) extends AbstractBehavior[Message | WsClientInputActor.Command](context) {

  override def onMessage(msg: Message | WsClientInputActor.Command): Behavior[Message | WsClientInputActor.Command] = msg match {
    case tm: TextMessage =>
      context.log.info("Got message: {}", tm.asTextMessage.getStrictText)
      client ! IncomingMessage("", tm.getStrictText, "", "")
      this
    case bm: BinaryMessage =>
      // ignore binary messages but drain content to avoid the stream being clogged
      bm.dataStream.runWith(Sink.ignore)
      this
    case ConnectionClosed =>
      context.log.info("Input stream closed")
      client ! ClientActor.ConnectionClosed
      Behaviors.stopped
  }
}

object WsClientInputActor {
  trait Command
  case object ConnectionClosed extends Command
}
/**
 * This actor will also be part of web layer, once implemented. It will transfer raw messages to websocket
 */
class WsClientOutputActor(context: ActorContext[OutgoingMessage | WsClientOutputActor.Command],
                          private var output: ActorRef[Message])
  extends AbstractBehavior[OutgoingMessage | WsClientOutputActor.Command](context) {

  override def onMessage(msg: OutgoingMessage | WsClientOutputActor.Command): Behavior[OutgoingMessage | WsClientOutputActor.Command] =
    msg match {
      case o: OutgoingMessage =>
        context.log.info("Got message: {}", o.text)
        output ! TextMessage(o.text)
        this
      case WsClientOutputActor.SetOutput(newOutput) =>
        output = newOutput
        this
      case WsClientOutputActor.ConnectionClosed =>
        // TODO: send closing message to output actor
        output ! TextMessage("CLOSED!")
        this
    }

  override def onSignal: PartialFunction[Signal, Behavior[OutgoingMessage | WsClientOutputActor.Command]] = {
    case PostStop =>
      context.log.info("WS disconnected")
      this
  }
}

object WsClientOutputActor {
  trait Command
  final case class SetOutput(output: ActorRef[Message]) extends Command
  case object ConnectionClosed extends Command
}