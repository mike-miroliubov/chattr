package org.chats
package service

import dto.{ConnectionClosed, InputMessageDto, OutputMessageDto, ServiceMessage, given}

import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.ws.*
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Flow, Keep, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.ActorSource
import spray.json.*

import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MessageService {
  private val sinks: mutable.Buffer[Sink[OutputMessageDto, _]] = mutable.Buffer()
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

  // Broadcast hub will dynamically connect multiple subscriber sinks as needed
  private val hub = BroadcastHub.sink[OutputMessageDto]

  def connect(userName: String): (Future[WebSocketUpgradeResponse], Future[Done], Connector) = {
    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:8081/api/connect?userName=$userName"))

    val (upgradeResponse, broadcast) =
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
        .toMat(hub)(Keep.both)
        .run()

    val connected = upgradeResponse.map { upgrade =>
      if (upgrade.response.status != StatusCodes.SwitchingProtocols) {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }

      Done
    }

    (upgradeResponse, connected, Connector(broadcast))
  }

  def send(message: InputMessageDto): Unit = {
    sendingActor ! message
  }

  def close(): Unit = {
    sendingActor ! ConnectionClosed
  }
}

private class Connector(val broadcast: Source[OutputMessageDto, _]) {
  def connect[D](incoming: Sink[OutputMessageDto, D]): D = {
    broadcast.toMat(incoming)(Keep.right).run()
  }
}