package org.chats

import dto.OutputMessageDto
import service.MessageService
import view.SimpleTextView

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.Sink

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

given system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
given executionContext: ExecutionContext = system.executionContext

object Main {
  private val chatView = SimpleTextView()
  private val messageService = MessageService()

  def main(args: Array[String]): Unit = {
    val userName = chatView.login()
    val (upgradeResponse, connected) = messageService.connect(userName)
    // Subscribe view to model changes
    val closed = messageService.subscribe(Sink.foreach(chatView.displayMessage))

    // in a real application you would not side effect here
    connected.onComplete(println)
    closed.onComplete {
      // TODO: we should try reconnect if websocket closed
      case s: Success[Any] =>
        println("Finished")
        System.exit(0)
      case _ =>
        println("Failed")
        System.exit(1)
    }

    sys.addShutdownHook {
      system.log.info("Shutting down")
      messageService.close()
      chatView.displayNote("Goodbye!")
    }

    while (true) {
      for {
        message <- chatView.readMessage()
      } yield messageService.send(message)
    }
  }
}