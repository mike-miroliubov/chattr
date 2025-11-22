package org.chats
package view

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.{KeyStroke, KeyType}
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.scaladsl.{Keep, Sink}
import org.chats.dto.{InputMessageDto, Message, OutputMessageDto}
import org.chats.service.MessengerClient

import java.util.UUID

class ChatView(
  private val recipient: String,
  private val userName: String,
  private val messengerClient: MessengerClient
) extends BaseView {
  private val window = BasicWindow(s"Chat with $recipient")
  private val panel = Panel(BorderLayout())
  private val messageArea = TextBox(TerminalSize(40, 10), TextBox.Style.MULTI_LINE).setReadOnly(true)
  private val inputBox = new TextBox(TerminalSize(30, 1))

  var onWindowClosed: () => Unit = () => {}

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

  private val chatId = recipient match {
    case s"g#${_}" => recipient
    case _ => Seq(userName, recipient).sorted.mkString("#")
  }

  messengerClient.getMessages(chatId).map(_.messages.foreach(displayMessage))

  // Subscribe view to model changes
  val (cancel, closed) = messengerClient.inputStream
    .viaMat(KillSwitches.single)(Keep.right)
    .toMat(Sink.foreach(displayMessage))(Keep.both)
    .run()

  messengerClient.closedStream.runForeach { _ =>
    system.terminate()
    sys.exit(1)
  }

  override def render(gui: MultiWindowTextGUI): Unit = {
    inputBox.takeFocus()
    gui.addWindowAndWait(window)
  }

  def displayMessage(message: InputMessageDto): Unit = {
    messageArea.addLine(s"${message.from}: ${message.text}")
    messageArea.handleKeyStroke(KeyStroke(KeyType.End))
  }

  def displayMessage(message: Message): Unit = {
    messageArea.addLine(s"${message.from}: ${message.text}")
    messageArea.handleKeyStroke(KeyStroke(KeyType.End))
  }

  private def sendMessage(): Unit = {
    val text = inputBox.getText
    if (!text.isBlank) {
      messengerClient.send(OutputMessageDto(UUID.randomUUID().toString, recipient, text))
      messageArea.addLine(s"You: $text")
      messageArea.handleKeyStroke(KeyStroke(KeyType.End))
      inputBox.setText("")
    }
  }

  private def closeWindow(): Unit = {
    window.close()
    cancel.shutdown()
    onWindowClosed()
  }
}
