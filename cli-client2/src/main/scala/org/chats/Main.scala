package org.chats

import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.{TerminalSize, TextColor}


object Main {
  def main(args: Array[String]): Unit = {
    val terminal = DefaultTerminalFactory().createTerminal()
    val screen = TerminalScreen(terminal)
    screen.startScreen()

    val gui = MultiWindowTextGUI(screen)
    gui.setTheme(new SimpleTheme(
      TextColor.ANSI.WHITE, // Foreground
      TextColor.ANSI.BLACK // Background
    ))
    showChatListWindow(gui)
  }

  private def showChatListWindow(gui: MultiWindowTextGUI): Unit = {
    val window = BasicWindow("Select chat")
    val panel = Panel(LinearLayout(Direction.VERTICAL))

    Seq("Alice", "Bob").foreach { userName =>
      panel.addComponent(Button(userName, () => {
        window.close()
        showChatWindow(gui, userName)
      }))
    }

    window.setComponent(panel)

    gui.addWindowAndWait(window)
  }

  private def showChatWindow(gui: MultiWindowTextGUI, userName: String): Unit = {
    val window = BasicWindow(s"Chat with $userName")
    val panel = Panel(BorderLayout())

    val messageArea = TextBox(TerminalSize(40, 10), TextBox.Style.MULTI_LINE).setReadOnly(true)
    panel.addComponent(messageArea, BorderLayout.Location.CENTER)

    val inputBox = new TextBox(TerminalSize(30, 1))

    def sendMessage(): Unit = {
      val text = inputBox.getText
      if (!text.isBlank) {
        messageArea.addLine(s"You: $text")
        inputBox.setText("")
      }
    }

    def closeWindow(): Unit = {
      window.close()
      showChatListWindow(gui)
    }

    inputBox.setInputFilter { (i, key) =>
      key.getKeyType match
        case KeyType.Enter =>
          sendMessage()
          false
        case KeyType.Escape =>
          closeWindow()
          false
        case _ => true
    }

    val inputPanel = Panel(LinearLayout(Direction.HORIZONTAL))
      .addComponent(
        Panel(LinearLayout(Direction.HORIZONTAL))
          .addComponent(Label(">"))
          .addComponent(inputBox).withBorder(Borders.singleLine())
      )

    panel
      .addComponent(inputPanel)

    window.setComponent(panel)
    inputBox.takeFocus()
    gui.addWindowAndWait(window)

  }
}