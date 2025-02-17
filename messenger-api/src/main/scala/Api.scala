package org.chats

import service.ClientActor.{GreetingsMessage, IncomingMessage, OutgoingMessage, ServiceCommand}
import service.{ClientActor, ClientManagerActor}

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketUpgrade}
import org.apache.pekko.http.scaladsl.model.{AttributeKeys, HttpRequest, HttpResponse, Uri}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
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
    // We'll create an actor that can handle our DTO objects: OutgoingMessage-s and ServiceCommand-s.
    // Pre-materialize the source to get the actor, we will pass it to our ClientActor.
    val (outputActor, outputMessageSource) = ActorSource.actorRef[ClientActor.OutgoingMessage | ClientActor.ServiceCommand](
      completionMatcher = {
        case ClientActor.ConnectionClosed =>
          system.log.debug("Closing WS stream")
      }, // maybe handle graceful logout
      failureMatcher = PartialFunction.empty[ClientActor.OutgoingMessage | ClientActor.ServiceCommand, Throwable],
      bufferSize = 256,
      overflowStrategy = OverflowStrategy.dropHead
    ).preMaterialize()

    // To handle websocket, we need a source that produces WS Message-s. Map our DTO messages to WS contract.
    val wsOutputMessageSource = outputMessageSource.map {
      case OutgoingMessage(_, text, _) => TextMessage(text)
    }

    for {
      // Spawn a new client actor, pass it the outputActor to communicate with the web socket.
      // Spawning is done by asking the system (ClientManagerActor) for a new actor by passing a ConnectClient message. This returns a Future of a new ClientActor
      // Scala cannot properly infer types here for some reason (probably because of a contravariant ActorRef[-T]).
      // We need to help it.
      clientActor <- system.ask[ActorRef[ClientActor.Command]](ref => ClientManagerActor.ConnectClient("new-client", outputActor, ref))(Timeout(3, TimeUnit.SECONDS))
    } yield {
      system.log.debug("Created new actors: {}, {}", clientActor, outputActor)
      clientActor ! GreetingsMessage

      // Sink WS messages to an actor by prepending a mapping Message -> IncomingMessage flow to an actor sink
      val wsInputMessageSink = Flow[Message]
        .mapConcat { // ignore binary messages, additionally draining them. mapConcat unpacks Options.
          case m: TextMessage => Some(m)
          case b: BinaryMessage =>
            b.dataStream.runWith(Sink.ignore)
            None
        }
        .map(m => IncomingMessage("", m.getStrictText, "", "")) // transform WS message to an IncomingMessage DTO
        .to(ActorSink.actorRef( // process messages with a ClientActor by dumping to an ActorSink
          clientActor,
          // when the client wants to disconnect, this message will be passed to the ClientActor,
          // it must relay it to the outputActor to close the WS stream
          onCompleteMessage = ClientActor.ConnectionClosed,
          onFailureMessage = e => {
            system.log.error("Exception when passing input messages", e)
            ClientActor.ConnectionClosed
          }))

      upgrade.handleMessagesWithSinkSource(wsInputMessageSink, wsOutputMessageSource)
    }
  }
}