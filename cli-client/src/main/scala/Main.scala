package org.chats

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.apache.pekko.stream.typed.scaladsl.ActorSource

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.io.StdIn.readLine
import scala.util.{Success, Try}

implicit val system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
implicit val executionContext: ExecutionContextExecutor = system.executionContext

trait ServiceMessage
case object ConnectionClosed extends ServiceMessage
final case class InputMessageDto(id: String, to: String, text: String)
final case class OutputMessageDto(id: String, from: String, text: String)

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
  val (sendingActor, source) = ActorSource.actorRef[InputMessageDto | ServiceMessage](
    completionMatcher = {
      case ConnectionClosed =>
        println("Closed the stream")
    },
    failureMatcher = PartialFunction.empty[InputMessageDto | ServiceMessage, Throwable],
    bufferSize = 256,
    overflowStrategy = OverflowStrategy.dropHead
  ).preMaterialize()

  val (upgradeResponse, closed) =
    source
      .map { case o: InputMessageDto => TextMessage(s"{\"text\":\"${o.text}\", \"to\":\"${o.to}\", \"id\":\"${o.id}\"}")}
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
    case _ =>
      println("Failed")
      System.exit(1)
  }

  sys.addShutdownHook {
    system.log.info("Shutting down")
    val x = Try {
      Await.ready(Future {
        sendingActor ! ConnectionClosed // this doesn't really work because probably the Actor system is already downing
      }, Duration(2, TimeUnit.SECONDS))
    }

    println("Goodbye!")
  }

//  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "someTaskName") { () =>
//    system.log.info("Shutting down")
//    sendingActor.ask[Done](ref => ConnectionClosed(ref))(timeout = Timeout(1, TimeUnit.SECONDS))
//      .andThen(_ => println("Goodbye"))
//  }

  while (true) {
    val message = readLine()
    sendingActor ! InputMessageDto("whatever", "foo", message)
  }
}
