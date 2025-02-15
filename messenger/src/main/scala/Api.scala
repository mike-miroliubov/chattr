package org.chats

import service.ClientActor.IncomingMessage
import service.{ClientActor, ClientManagerActor}

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import org.apache.pekko.http.scaladsl.model.{AttributeKeys, HttpRequest, HttpResponse, Uri}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.impl.ActorPublisher
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.ActorSource
import org.apache.pekko.util.Timeout

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

object Api extends Directives {
  def handleWsRequest(request: HttpRequest): Future[HttpResponse] = request match {
    // WS connections are only allowed at /api/connect endpoint
    case req@HttpRequest(GET, Uri.Path("/api/connect"), _, _, _) =>
      // handle websocket upgrade event
      req.attribute(AttributeKeys.webSocketUpgrade) match {
        case Some(upgrade) =>
          //val src: Source[Message, ActorRef[Message]] = ActorSource.actorRef[Message](_ => {}, {case m if !m.isText => RuntimeException()}, Int.MaxValue, OverflowStrategy.fail)

          // spawn a new client actor, we will use it to communicate with this web socket later
          val actorF = system.ask(ref => ClientManagerActor.ConnectClient("new-client", ref))(Timeout(3, TimeUnit.SECONDS))

          actorF.map { a =>
            system.log.info("Created new actor {}", a)
            // Scala cannot properly infer types here for some reason (probably because of a contravariant ActorRef[-T])
            // So we need this ugly casting
            a.asInstanceOf[ActorRef[ClientActor.Command]] ! IncomingMessage("", "Joined the chat", "", "")
            upgrade.handleMessagesWithSinkSource(
              Sink.foreach[Message](handleIncomingMessage(a.asInstanceOf[ActorRef[ClientActor.Command]], "new-client")),
              Source(1 to 100).throttle(1, FiniteDuration(1, TimeUnit.SECONDS)).map(i => TextMessage(s"$i")))
          }
        //upgrade.handleMessages(greeterWebSocketService)
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

  private def handleIncomingMessage(actor: ActorRef[ClientActor.Command], userId: String)(msg: Message): Unit = msg match {
    case tm: TextMessage => actor ! IncomingMessage("", tm.getStrictText, userId, "")
    case bm: BinaryMessage =>
      // ignore binary messages but drain content to avoid the stream being clogged
      bm.dataStream.runWith(Sink.ignore)
  }

  private val greeterWebSocketService: Flow[Message, TextMessage, NotUsed] =
    Flow[Message]
      .mapConcat {
        // we match but don't actually consume the text message here,
        // rather we simply stream it back as the tail of the response
        // this means we might start sending the response even before the
        // end of the incoming message has been received
        case tm: TextMessage => TextMessage(Source.single("You said: ") ++ tm.textStream) :: Nil
        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
}