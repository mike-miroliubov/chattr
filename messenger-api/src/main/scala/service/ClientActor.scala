package org.chats
package service

import service.ClientActor.*

import org.apache.pekko.actor.ActorRef as UntypedActorRef
import org.apache.pekko.actor.typed.receptionist.{Receptionist, ServiceKey}
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import org.apache.pekko.util.Timeout

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * This is the main user actor. It handles user's incoming and outgoing messages.
 * Each connected user has 1 actor like this, it contains main message routing logic
 */
class ClientActor(context: ActorContext[ClientActor.Command],
                  userId: String,
                  outputActor: ActorRef[OutgoingMessage | ServiceCommand],
                  router: UntypedActorRef,
                 ) extends AbstractBehavior[ClientActor.Command](context) {
  context.log.info("User {} joined", userId)

  private val peers = ConcurrentHashMap[String, ActorRef[OutgoingMessage] | Future[ActorRef[OutgoingMessage]]]()

  private val clientServiceKey = ServiceKey[OutgoingMessage](userId)
  context.system.receptionist ! Receptionist.Register(clientServiceKey, context.self)

  override def onMessage(msg: ClientActor.Command): Behavior[ClientActor.Command] = msg match {
    case in: IncomingMessage =>
      context.log.info("Got message: {}", in.text)

      // This is a very naive lookup, fix it with a better one. This will not scale
      val value = peers.computeIfAbsent(in.to, key => {
        context.system.receptionist.ask[Receptionist.Listing](ref => Receptionist.Find(ServiceKey[OutgoingMessage](in.to), ref))(Timeout(3, TimeUnit.SECONDS))
          .map { listing => listing.serviceInstances(ServiceKey[OutgoingMessage](in.to)).head }
      })

      value match {
        case toActorFuture: Future[ActorRef[OutgoingMessage]] =>
          toActorFuture.onComplete(sendMessageOnLookupComplete(in))
        case toActor: ActorRef[OutgoingMessage] =>
          toActor ! OutgoingMessage(in.messageId, in.text, userId)
      }

      this
    case out: OutgoingMessage =>
      outputActor ! out
      this
    case GreetingsMessage =>
      outputActor ! OutgoingMessage("", "You joined the chat", "")
      this
    case ConnectionClosed =>
      context.log.info("Client {} disconnected", userId)
      outputActor ! ConnectionClosed
      Behaviors.stopped
  }

  private def sendMessageOnLookupComplete(in: IncomingMessage)(result: Try[ActorRef[OutgoingMessage]]): Unit = result match {
    case Failure(exception) => ??? // this will happen if an actor is missing
    case Success(toActor) =>
      peers.put(in.to, toActor)
      toActor ! OutgoingMessage(in.messageId, in.text, userId)
  }

  override def onSignal: PartialFunction[Signal, Behavior[ClientActor.Command]] = {
    case PostStop =>
      context.log.info("Client {} actor stopped", userId)
      this
  }
}

object ClientActor {
  sealed trait Command
  final case class IncomingMessage(messageId: String, text: String, to: String, from: String) extends Command
  final case class OutgoingMessage(messageId: String, text: String, from: String) extends Command
  case object GreetingsMessage extends Command

  sealed trait ServiceCommand extends Command
  case object ConnectionClosed extends ServiceCommand
  // TODO: maybe add replyTo: ActorRef[Ack]
  // final case class Ack(messageId: String) extends ServiceCommand
}