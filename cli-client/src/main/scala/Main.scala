package org.chats

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.ActorSource

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn.readLine
import scala.util.Success

implicit val system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
implicit val executionContext: ExecutionContextExecutor = system.executionContext

object Main extends App {
  println("Welcome! Please enter your username")
  val userName = readLine()

  val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:8081/api/connect?userName=$userName"))

  // Future[Done] is the materialized value of Sink.foreach,
  // emitted when the stream completes
  val incoming: Sink[Message, Future[Done]] =
    Sink.foreach[Message] {
      case message: TextMessage.Strict =>
        println(message.text)
      case _ =>
      // ignore other message types
    }

  // send this as a message over the WebSocket
  val (sendingActor, source) = ActorSource.actorRef(
    completionMatcher = PartialFunction.empty[TextMessage, Unit],
    failureMatcher = PartialFunction.empty[TextMessage, Throwable],
    bufferSize = 256,
    overflowStrategy = OverflowStrategy.dropHead
  ).preMaterialize()

  val (upgradeResponse, closed) =
    source
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

  // in a real application you would not side effect here
  connected.onComplete(println)
  closed.onComplete {
    case s: Success[Any] =>
      println("Finished")
      System.exit(0)
    case _ => println("Failed")
  }

  while (true) {
    val message = readLine()
    sendingActor ! TextMessage(s"{\"text\":\"$message\", \"to\":\"foo\", \"id\":\"whatever\"}")
  }
}
