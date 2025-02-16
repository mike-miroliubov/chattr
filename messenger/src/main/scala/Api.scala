package org.chats

import service.ClientActor.IncomingMessage
import service.{ClientActor, ClientManagerActor}

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.PoisonPill
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage, WebSocketUpgrade}
import org.apache.pekko.http.scaladsl.model.{AttributeKeys, HttpRequest, HttpResponse, Uri}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.{ActorSink, ActorSource}
import org.apache.pekko.util.Timeout

import java.util.concurrent.TimeUnit
import scala.concurrent.Future

object Api extends Directives {
  def handleWsRequest(request: HttpRequest): Future[HttpResponse] = request match {
    // WS connections are only allowed at /api/connect endpoint
    case req @ HttpRequest(GET, Uri.Path("/api/connect"), _, _, _) =>
      // handle websocket upgrade event
      req.attribute(AttributeKeys.webSocketUpgrade) match {
        case Some(upgrade) => handleWebSocketUpgrade(upgrade)
        case None => Future {
          HttpResponse(400, entity = "Not a valid websocket request!")
        }
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      Future {
        HttpResponse(404, entity = "Unknown resource!")
      }
  }

  private def handleWebSocketUpgrade(upgrade: WebSocketUpgrade): Future[HttpResponse] = {
    // Create a source, backed by an actor so we could send messages to websocket
    // Pre-materialize the source to get the actor, we will pass it to our actor.
    val (outputActor, source) = ActorSource.actorRef[Message](
      completionMatcher = PartialFunction.empty,
      failureMatcher = PartialFunction.empty[Message, Throwable],
      bufferSize = 256,
      overflowStrategy = OverflowStrategy.dropHead
    ).preMaterialize()

    // spawn a new client actor, we will use it to communicate with this web socket later
    val clientActorFuture = system.ask(ref => ClientManagerActor.ConnectClient("new-client", ref))(Timeout(3, TimeUnit.SECONDS))
    val wsInputActorFuture = system.ask(ref => ClientManagerActor.ConnectWs("new-client", outputActor, ref))(Timeout(3, TimeUnit.SECONDS))

    for {
      clientActor <- clientActorFuture
      wsActor <- wsInputActorFuture
    } yield {
      system.log.info("Created new actor {}", clientActor)
      // Scala cannot properly infer types here for some reason (probably because of a contravariant ActorRef[-T])
      // So we need this ugly casting
      clientActor.asInstanceOf[ActorRef[ClientActor.Command]] ! IncomingMessage("", "Joined the chat", "", "")

      val sink: Sink[Message | PoisonPill.type, NotUsed] = ActorSink.actorRef(wsActor.asInstanceOf[ActorRef[Message | PoisonPill]],
        onCompleteMessage = PoisonPill, // TODO: this doesn't seem to be working
        onFailureMessage = e => {
          system.log.error("Exception when passing input messages", e)
          PoisonPill
        })

      outputActor ! TextMessage("hello")

      upgrade.handleMessagesWithSinkSource(sink, source)
    }
  }
}