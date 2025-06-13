package org.chats
package service

import dto.{ConnectionClosed, InputMessageDto, OutputMessageDto, ServiceMessage, given}

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.ws.*
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Flow, Keep, RestartFlow, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.ActorSource
import org.apache.pekko.stream.{OverflowStrategy, RestartSettings}
import org.apache.pekko.{Done, NotUsed}
import spray.json.*

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

class MessengerClient(userName: String, val host: String, val port: Int) {
  // Broadcast hub will dynamically connect multiple subscriber sinks as needed
  private val hub = BroadcastHub.sink[InputMessageDto]
  private val (outputActor: ActorRef[OutputMessageDto | ServiceMessage], input, closed) = connect(userName)

  def inputStream: Source[InputMessageDto, _] = input
  def closedStream: Source[Done, _] = closed

  private def connect(userName: String): (ActorRef[OutputMessageDto | ServiceMessage], Source[InputMessageDto, _], Source[Done, NotUsed]) = {
    val (connected, actor, wsInput) = connectWebSocket(userName)
    // run the connector against a no-op sink to get a future of when the websocket closes
    // alternatively we could just pre-materialize it, I guess
    val done = wsInput.runWith(Sink.ignore)

    val connectionClosed = Source.future(done).recover {
      _ => println("Could not reconnect, exiting")
      Done
    }

    // when a WS connection is lost, wsInput stream closes with an exception,
    // we need to handle it so that the subscriber doesn't drop
    (actor, wsInput.recover { _ => InputMessageDto("", "", "Connection lost") }, connectionClosed)
  }

  private def connectWebSocket(userName: String): (Source[Done, NotUsed], ActorRef[OutputMessageDto | ServiceMessage], Source[InputMessageDto, _]) = {
    val (sendingActor, source) = ActorSource.actorRef[OutputMessageDto | ServiceMessage](
      completionMatcher = {
        case ConnectionClosed =>
          println("Closed the stream")
      },
      failureMatcher = PartialFunction.empty[OutputMessageDto | ServiceMessage, Throwable],
      bufferSize = 256,
      overflowStrategy = OverflowStrategy.dropHead
    ).preMaterialize()

    val (connected, webSocketFlow) = getWebsocketFlowWithRetry(userName)

    val broadcast =
      source
        .map { case o: OutputMessageDto => TextMessage(o.toJson.compactPrint) }
        // route input flow to websocket, keep the materialized Future[WebSocketUpgradeResponse]
        .viaMat(webSocketFlow)(Keep.none)
        // transform output flow
        .mapConcat {
          case message: TextMessage => Some(message)
          case message: BinaryMessage =>
            message.dataStream.runWith(Sink.ignore)
            None
        }
        .mapAsync(1)(_.toStrict(FiniteDuration(3, TimeUnit.SECONDS)))
        .map(m => m.text.parseJson.convertTo[InputMessageDto])
        // route output flow to sink, also keep the Future[Done]
        .toMat(hub)(Keep.right)
        .run()

    (connected, sendingActor, broadcast)
  }

  private def getWebsocketFlowWithRetry(userName: String): (Source[Done, NotUsed], Flow[Message, Message, NotUsed]) = {
    val (connectedQueue, connected) = Source.queue[Done](1, overflowStrategy = OverflowStrategy.dropHead).preMaterialize()

    val restartSettings = RestartSettings(
        minBackoff = 1.seconds,
        maxBackoff = 5.seconds,
        randomFactor = 0.2
      )
      .withMaxRestarts(5, 1.minute)

    val webSocketFlow = RestartFlow.withBackoff(restartSettings) { () =>
      val (upgradeResponse, originalFlow) = Http()
        .webSocketClientFlow(WebSocketRequest(s"ws://${host}:${port}/api/connect?userName=$userName"))
        .preMaterialize()

      // Because connection might be reattempted many times, we need a stream rather than a Future.
      // Handle each of the futures by sending a message to a stream
      upgradeResponse.onComplete {
        case Failure(exception) => println(s"Connection failed: ${exception.getMessage}")
        case Success(upgrade) =>
          if (upgrade.response.status != StatusCodes.SwitchingProtocols) {
            throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
          }

          connectedQueue.offer(Done)
      }

      originalFlow
    }

    (connected, webSocketFlow)
  }

  def send(message: OutputMessageDto): Unit = {
    outputActor ! message
  }

  def close(): Unit = {
    outputActor ! ConnectionClosed
  }
}