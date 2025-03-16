package org.chats

import service.MessageService
import view.SimpleTextView

import org.apache.pekko.Done
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import scala.concurrent.{ExecutionContext, Future}

given system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
given executionContext: ExecutionContext = system.executionContext

object Main {
  private val chatView = SimpleTextView()
  private val messageService = MessageService()

  def main(args: Array[String]): Unit = {
    val userName = chatView.login()
    val inputStream = messageService.connect(userName)
    // Subscribe view to model changes
    val closed = inputStream.runForeach(chatView.displayMessage)

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