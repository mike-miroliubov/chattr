package org.chats
package service

import dto.{ConnectionClosed, InputMessageDto, OutputMessageDto, ServiceMessage, given}

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.ws.*
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.ActorSource
import spray.json.*

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MessageService(userName: String) {
  private val webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:8081/api/connect?userName=$userName"))

  // send this as a message over the WebSocket
  private val (sendingActor, source) = ActorSource.actorRef[InputMessageDto | ServiceMessage](
    completionMatcher = {
      case ConnectionClosed =>
        println("Closed the stream")
    },
    failureMatcher = PartialFunction.empty[InputMessageDto | ServiceMessage, Throwable],
    bufferSize = 256,
    overflowStrategy = OverflowStrategy.dropHead
  ).preMaterialize()

  def subscribe[D](incoming: Sink[OutputMessageDto, D]): (Future[WebSocketUpgradeResponse], Future[Done], D) = {
    val (upgradeResponse, closed) =
      source
        .map { case o: InputMessageDto => TextMessage(o.toJson.compactPrint) }
        // route input flow to websocket, keep the materialized Future[WebSocketUpgradeResponse]
        .viaMat(webSocketFlow)(Keep.right)
        // transform output flow
        .mapConcat {
          case message: TextMessage => Some(message)
          case message: BinaryMessage =>
            message.dataStream.runWith(Sink.ignore)
            None
        }
        .mapAsync(1)(_.toStrict(FiniteDuration(3, TimeUnit.SECONDS)))
        .map(m => m.text.parseJson.convertTo[OutputMessageDto])
        // route output flow to sink, also keep the Future[Done]
        .toMat(incoming)(Keep.both)
        .run()

    val connected = upgradeResponse.map { upgrade =>
      if (upgrade.response.status != StatusCodes.SwitchingProtocols) {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }

      Done
    }

    (upgradeResponse, connected, closed)
  }

  def send(message: InputMessageDto): Unit = {
    sendingActor ! message
  }

  def close(): Unit = {
    sendingActor ! ConnectionClosed
  }
}
