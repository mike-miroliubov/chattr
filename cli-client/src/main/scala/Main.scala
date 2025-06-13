package org.chats

import service.MessengerClient
import view.SimpleTextView

import com.monovore.decline.{Command, Opts}
import org.apache.pekko.Done
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import cats.syntax.all._


import scala.concurrent.{ExecutionContext, Future}

given system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
given executionContext: ExecutionContext = system.executionContext

object Main {
  private val chatView = SimpleTextView()

  private case class ChatConfig(host: String, port: Int)
  private val port = Opts.option[Int]("port", "Port to connect to", short = "p")
    .withDefault(8081)
    .validate("Port number must be a positive integer") { _ > 0 }
  private val host = Opts.option[String]("host", "Host to connect to", short = "h")
    .withDefault("localhost")
    .validate("Host must not be empty") { _.nonEmpty }
  private val options = (host, port).mapN(ChatConfig.apply)
  private val chatCommand = Command(name = "chat", header = "A command line chat client", helpFlag = true) { options }


  def main(args: Array[String]): Unit = {
    chatCommand.parse(args) match {
      case Left(help) if help.errors.isEmpty =>
        println(help)
        sys.exit(0)
      case Left(help) =>
        System.err.println(help)
        sys.exit(1)
      case Right(config) => startChat(config)
    }
  }

  private def startChat(config: ChatConfig): Unit = {
    val userName = chatView.login()
    val messengerClient = MessengerClient(userName, config.host, config.port)
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