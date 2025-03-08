package org.chats

import model.Model

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.apache.pekko.stream.scaladsl.Sink

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

  val model = Model(userName)

  // Future[Done] is the materialized value of Sink.foreach,
  // emitted when the stream completes
  val incoming: Sink[Message, Future[Done]] =
    Sink.foreach[Message] {
      case message: TextMessage.Strict =>
        println(message.text)
      case _ =>
      // ignore other message types
    }

  val (upgradeResponse, connected, closed) = model.start(incoming)

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
        model.close()
      }, Duration(3, TimeUnit.SECONDS))
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
    model.send(InputMessageDto("whatever", "foo", message))
  }
}
