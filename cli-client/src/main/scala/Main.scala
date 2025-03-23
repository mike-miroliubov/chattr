package org.chats

import service.MessengerClient
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

  def main(args: Array[String]): Unit = {
    val userName = chatView.login()
    val messengerClient = MessengerClient(userName)
    // Subscribe view to model changes
    val closed = messengerClient.inputStream.runForeach(chatView.displayMessage)

    messengerClient.closedStream.runForeach { _ =>
      system.terminate()
      sys.exit(1)
    }

    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "websocketClose") { () =>
      messengerClient.close()
      chatView.displayNote("Goodbye!")
      Future.successful(Done)
    }

    while (true) {
      for {
        message <- chatView.readMessage()
      } yield messengerClient.send(message)
    }
  }
}