package org.chats

import dto.OutputMessageDto
import service.MessengerClient
import view.*

import cats.syntax.all.*
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.monovore.decline.{Command, Opts}
import org.apache.pekko.Done
import org.apache.pekko.actor.CoordinatedShutdown
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.chats.config.Settings
import org.chats.settings.initConfig
import org.slf4j.LoggerFactory
import pureconfig.ConfigSource

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Using}

val logger = LoggerFactory.getLogger("Main")
given system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
given executionContext: ExecutionContext = system.executionContext

object Main {
  private val chatView = SimpleTextView()

  private case class ChatConfig(host: String, port: Int, apiPort: Int)

  private val settings = ConfigSource.fromConfig(initConfig("application.conf")).loadOrThrow[Settings]
  private val mainCommand = Command(name = "chat", header = "A command line chat client", helpFlag = true) { Opts.help.orNone }


  def main(args: Array[String]): Unit = {
    logger.info("Starting")
    mainCommand.parse(args) match {
      case Left(help) if help.errors.isEmpty =>
        println(help)
        sys.exit(0)
      case Left(help) =>
        System.err.println(help)
        sys.exit(1)
      case Right(_) =>
        startChat()
    }
  }

  private def startChat(): Unit = {
    Using(DefaultTerminalFactory().createTerminal()) { terminal =>
      val screen = TerminalScreen(terminal)
      screen.startScreen()

      val gui = MultiWindowTextGUI(screen)
      gui.setTheme(THEME)

      val loginView = LoginView()
      val chatListView = ChatListView()
      loginView.onLogin = userName => {
        val messengerClient = MessengerClient(userName, settings.chattr.server)

        def getChats(): Unit = {
          messengerClient.getChats().onComplete {
            case Failure(exception) => logger.error("Failed to get chats", exception)
            case Success(chats) => chatListView.setChats(chats.chats.map(it => {
              val displayText = if (it.fromUserId == userName) s"You: ${it.lastText}" else it.lastText
              val chatRecipient = it.id.split("#").find(_ != userName).getOrElse("")
              ChatRow(chatRecipient, displayText)
            }))
          }
        }

        getChats()

        chatListView.onChatSelect = chat => {
          val cView = ChatView(chat, userName, messengerClient)
          cView.onWindowClosed = () => {
            getChats()
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