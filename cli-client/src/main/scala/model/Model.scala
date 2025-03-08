package org.chats
package model

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink}
import org.apache.pekko.stream.typed.scaladsl.ActorSource

import scala.concurrent.Future

class Model(userName: String) {
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


  def start(incoming: Sink[Message, Future[Done]]): (Future[WebSocketUpgradeResponse], Future[Done], Future[Done]) = {
    val (upgradeResponse, closed) =
      source
        .map { case o: InputMessageDto => TextMessage(s"{\"text\":\"${o.text}\", \"to\":\"${o.to}\", \"id\":\"${o.id}\"}") }
        .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
        .toMat(incoming)(Keep.both) // also keep the Future[Done]
        .run()

    val connected = upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
        Future.successful(Done)
      } else {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }
    }

    (upgradeResponse, connected, closed)
  }

  def send(message: InputMessageDto): Unit = {
    sendingActor ! message
  }

  def close(): Unit = {
    sendingActor ! ConnectionClosed // this doesn't really work because probably the Actor system is already downing
  }
}
