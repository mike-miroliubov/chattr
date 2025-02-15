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
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import org.apache.pekko.util.Timeout

import java.util.concurrent.TimeUnit
import scala.util.Success

object Api extends Directives {
  def handleWsRequest(request: HttpRequest): HttpResponse = request match {
    // WS connections are only allowed at /api/connect endpoint
    case req @ HttpRequest(GET, Uri.Path("/api/connect"), _, _, _) =>
      // handle websocket upgrade event
      req.attribute(AttributeKeys.webSocketUpgrade) match {
        case Some(upgrade) =>
          // spawn a new client actor, we will use it to communicate with this web socket later
          val actor = system.ask(ref => ClientManagerActor.ConnectClient("new-client", ref))(Timeout(3, TimeUnit.SECONDS))
          actor.onComplete {
            case Success(r) =>
              system.log.info("Created new actor {}", r)
              // Scala cannot properly infer types here for some reason (probably because of a contravariant ActorRef[-T])
              // So we need this ugly casting
              r.asInstanceOf[ActorRef[ClientActor.Command]] ! IncomingMessage("", "Joined the chat", "", "")
          }
          upgrade.handleMessages(greeterWebSocketService)
        case None => HttpResponse(400, entity = "Not a valid websocket request!")
      }
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
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