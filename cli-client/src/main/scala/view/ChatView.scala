package org.chats
package view

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.{KeyStroke, KeyType}
import org.chats.dto.InputMessageDto

class ChatView(
  private val userName: String,
) extends BaseView {
  private val window = BasicWindow(s"Chat with $userName")
  private val panel = Panel(BorderLayout())
  private val messageArea = TextBox(TerminalSize(40, 10), TextBox.Style.MULTI_LINE).setReadOnly(true)
  private val inputBox = new TextBox(TerminalSize(30, 1))

  var onWindowClosed: () => Unit = () => {}
  var onMessageSent: String => Unit = _ => {}

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

  private val inputPanel = Panel(LinearLayout(Direction.HORIZONTAL))
    .addComponent(
      Panel(LinearLayout(Direction.HORIZONTAL))
        .addComponent(Label(">"))
        .addComponent(inputBox).withBorder(Borders.singleLine())
    )

  panel
    .addComponent(messageArea, BorderLayout.Location.CENTER)
    .addComponent(inputPanel)

  window.setComponent(panel)

  override def render(gui: MultiWindowTextGUI): Unit = {
    inputBox.takeFocus()
    gui.addWindowAndWait(window)
  }

  def displayMessage(message: InputMessageDto): Unit = {
    messageArea.addLine(s"${message.from}: ${message.text}")
    messageArea.handleKeyStroke(KeyStroke(KeyType.End))
  }

  private def sendMessage(): Unit = {
    val text = inputBox.getText
    if (!text.isBlank) {
      onMessageSent(text)
      messageArea.addLine(s"You: $text")
      messageArea.handleKeyStroke(KeyStroke(KeyType.End))
      inputBox.setText("")
    }
  }

  private def closeWindow(): Unit = {
    window.close()
    onWindowClosed()
  }
}
