package org.chats

import service.ClientActor.{IncomingMessage, OutgoingMessage}
import service.{ClientActor, ClientManagerActor}

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.PoisonPill
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketUpgrade}
import org.apache.pekko.http.scaladsl.model.{AttributeKeys, HttpRequest, HttpResponse, Uri}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import org.apache.pekko.stream.typed.javadsl.ActorSource
import org.apache.pekko.stream.typed.scaladsl.ActorSink
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
    // Create a source, backed by a queue so we could connect actor with a web socket.
    // Pre-materialize the source to get that queue, we will pass it to an actor.
    // There's probably a much better way of doing this
    val (outQueue: SourceQueueWithComplete[OutgoingMessage], outSrc: Source[OutgoingMessage, NotUsed]) =
      Source.queue[OutgoingMessage](16, OverflowStrategy.fail).preMaterialize()

    // spawn a new client actor, we will use it to communicate with this web socket later
    val clientActorFuture = system.ask(ref => ClientManagerActor.ConnectClient("new-client", outQueue, ref))(Timeout(3, TimeUnit.SECONDS))
    val wsInputActorFuture = system.ask(ref => ClientManagerActor.ConnectWs("new-client", ref))(Timeout(3, TimeUnit.SECONDS))

    for {
      clientActor <- clientActorFuture
      wsActor <- wsInputActorFuture
    } yield {
      system.log.info("Created new actor {}", clientActor)
      // Scala cannot properly infer types here for some reason (probably because of a contravariant ActorRef[-T])
      // So we need this ugly casting
      clientActor.asInstanceOf[ActorRef[ClientActor.Command]] ! IncomingMessage("", "Joined the chat", "", "")

      val sink: Sink[Message | PoisonPill.type, NotUsed] = ActorSink.actorRef(wsActor.asInstanceOf[ActorRef[Message | PoisonPill]], PoisonPill, e => {
        system.log.error("Exception when passing input messages", e)
        PoisonPill
      })

      upgrade.handleMessagesWithSinkSource(
        sink,
        //Sink.foreach[Message](handleIncomingMessage(clientActor.asInstanceOf[ActorRef[ClientActor.Command]], "new-client")),
        outSrc.map(o => TextMessage(o.text)))
    }
  }

  private def handleIncomingMessage(actor: ActorRef[ClientActor.Command], userId: String)(msg: Message): Unit = msg match {
    case tm: TextMessage => actor ! IncomingMessage("", tm.getStrictText, userId, "")
    case bm: BinaryMessage =>
      // ignore binary messages but drain content to avoid the stream being clogged
      bm.dataStream.runWith(Sink.ignore)
  }
}