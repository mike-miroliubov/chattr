package org.chats

import service.MessengerClient
import view.{ChatListView, ChatView, LoginView, SimpleTextView}

import com.monovore.decline.{Command, Opts}
import org.apache.pekko.Done
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import cats.syntax.all.*
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.chats.dto.OutputMessageDto

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

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
  private val mainCommand = Command(name = "chat", header = "A command line chat client", helpFlag = true) { options }


  def main(args: Array[String]): Unit = {
    mainCommand.parse(args) match {
      case Left(help) if help.errors.isEmpty =>
        println(help)
        sys.exit(0)
      case Left(help) =>
        System.err.println(help)
        sys.exit(1)
      case Right(config) =>
        startChat2(config)
        //startChat(config)
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

  private def startChat2(config: ChatConfig): Unit = {
    Using(DefaultTerminalFactory().createTerminal()) { terminal =>
      val screen = TerminalScreen(terminal)
      screen.startScreen()

      val gui = MultiWindowTextGUI(screen)
      gui.setTheme(new SimpleTheme(
        TextColor.ANSI.WHITE, // Foreground
        TextColor.ANSI.BLACK // Background
      ))

      val loginView = LoginView()
      val chatListView = ChatListView()
      loginView.onLogin = userName => {
        val messengerClient = MessengerClient(userName, config.host, config.port)

        chatListView.onChatSelect = chat => {
          val cView = ChatView(chat)

          // Subscribe view to model changes
          val (killSwitch, closed) = messengerClient.inputStream
            .viaMat(KillSwitches.single)(Keep.right)
            .toMat(Sink.foreach(cView.displayMessage))(Keep.both)
            .run()

          messengerClient.closedStream.runForeach { _ =>
            system.terminate()
            sys.exit(1)
          }

          cView.onMessageSent = text => {
            messengerClient.send(OutputMessageDto(UUID.randomUUID().toString, chat, text))
          }
          
          cView.onWindowClosed = () => {
            killSwitch.shutdown()
            chatListView.render(gui)
          }

          cView.render(gui)
        }


        CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "websocketClose") { () =>
          messengerClient.close()
          chatView.displayNote("Goodbye!")
          Future.successful(Done)
        }

        chatListView.render(gui)
      }


      loginView.render(gui)
    }
  }
}