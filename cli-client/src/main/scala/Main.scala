package org.chats

import dto.OutputMessageDto
import service.MessageService
import view.SimpleTextView

import org.apache.pekko.Done
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Sink

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import scala.concurrent.duration.*

given system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
given executionContext: ExecutionContext = system.executionContext

object Main {
  private val chatView = SimpleTextView()
  private val messageService = MessageService()

  def main(args: Array[String]): Unit = {
    val userName = chatView.login()
    val (upgradeResponse, connected, connector) = messageService.connect(userName)
    // Subscribe view to model changes
    val closed = connector.connect(Sink.foreach(chatView.displayMessage))

    // in a real application you would not side effect here
    connected.onComplete(println)
    closed.onComplete {
      // TODO: we should try reconnect if websocket closed
      case s: Success[Any] =>
        println("Finished")
        //System.exit(0)
      case _ =>
        println("Failed")
        //System.exit(1)
    }

    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "websocketClose") { () =>
      messageService.close()
      chatView.displayNote("Goodbye!")
      Future.successful(Done)
    }

    while (true) {
      for {
        message <- chatView.readMessage()
      } yield messageService.send(message)
    }
  }
}