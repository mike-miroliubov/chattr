package org.chats

import view.{ChatListView, ChatView}

import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory

import scala.util.Using


object Main {
  def main(args: Array[String]): Unit = {
    Using(DefaultTerminalFactory().createTerminal()) { terminal =>
      val screen = TerminalScreen(terminal)
      screen.startScreen()

      val gui = MultiWindowTextGUI(screen)
      gui.setTheme(new SimpleTheme(
        TextColor.ANSI.WHITE, // Foreground
        TextColor.ANSI.BLACK // Background
      ))
      
      val chatListView = ChatListView()
      chatListView.onChatSelect = chat => {
        ChatView(chat, onWindowClosed = () => { chatListView.render(gui) }).render(gui)
      }

      chatListView.render(gui)
    }
  }
}