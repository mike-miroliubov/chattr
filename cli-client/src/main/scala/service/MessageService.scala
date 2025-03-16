package org.chats
package service

import dto.{ConnectionClosed, OutputMessageDto, InputMessageDto, ServiceMessage, given}

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.ws.*
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.ActorSource
import org.apache.pekko.{Done, NotUsed}
import spray.json.*

import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class MessageService {
  // Broadcast hub will dynamically connect multiple subscriber sinks as needed
  private val hub = BroadcastHub.sink[InputMessageDto]

  private val (reconnectQueue, reconnect) = Source.queue[Done](bufferSize = 1, OverflowStrategy.dropHead).preMaterialize()
  private val (inputQueue, input) = Source.queue[Source[InputMessageDto, _]](bufferSize = 16, OverflowStrategy.dropHead).preMaterialize()
  private var outputActor: Option[ActorRef[OutputMessageDto | ServiceMessage]] = None

  def connect(userName: String): Source[InputMessageDto, _] = {
    reconnect.runForeach { _ =>
      val (upgradeResponse, connected, actor, wsInput) = connectWebSocket(userName)
      // run the connector against a no-op sink to get a future of when the websocket closes
      // alternatively we could just pre-materialize it, I guess
      val done = wsInput.runWith(Sink.ignore)

      // set the actor. We must always create a new actor and stream on re-connecting because the actors dies then
      // websocket closes
      outputActor = Some(actor)

      done.onComplete({
        case s: Success[Any] =>
        case f: Failure[Any] =>
          println("Will reconnect in 5 sec")
          Thread.sleep(5000)
          reconnectQueue.offer(Done)
      })

      // push the websocket input stream into a stream
      // when a WS connection is lost, wsInput stream closes with an exception,
      // we need to handle it so that the subscriber doesn't drop
      inputQueue.offer(wsInput.recover { _ => InputMessageDto("", "", "Connection lost") })
    }

    // kick off the connection
    reconnectQueue.offer(Done)

    // flatten so that the client gets a stream of DTO objects, not a stream of streams
    input.flatten
  }

  private def connectWebSocket(userName: String): (Future[WebSocketUpgradeResponse], Future[Done], ActorRef[OutputMessageDto | ServiceMessage], Source[InputMessageDto, _]) = {
    val (sendingActor, source) = ActorSource.actorRef[OutputMessageDto | ServiceMessage](
      completionMatcher = {
        case ConnectionClosed =>
          println("Closed the stream")
      },
      failureMatcher = PartialFunction.empty[OutputMessageDto | ServiceMessage, Throwable],
      bufferSize = 256,
      overflowStrategy = OverflowStrategy.dropHead
    ).preMaterialize()

    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:8081/api/connect?userName=$userName"))

    val (upgradeResponse, broadcast) =
      source
        .map { case o: OutputMessageDto => TextMessage(o.toJson.compactPrint) }
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
        .map(m => m.text.parseJson.convertTo[InputMessageDto])
        // route output flow to sink, also keep the Future[Done]
        .toMat(hub)(Keep.both)
        .run()

    val connected = upgradeResponse.map { upgrade =>
      if (upgrade.response.status != StatusCodes.SwitchingProtocols) {
        throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
      }

      Done
    }

    (upgradeResponse, connected, sendingActor, broadcast)
  }

  def send(message: OutputMessageDto): Unit = {
    outputActor match {
      case Some(a) => a ! message
      case None => ???
    }
  }

  def close(): Unit = {
    outputActor match {
      case Some(a) => a ! ConnectionClosed
      case None => ???
    }
  }
}