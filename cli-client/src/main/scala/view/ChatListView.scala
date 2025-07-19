package org.chats
package view

import com.googlecode.lanterna.{TerminalSize, TextColor}
import com.googlecode.lanterna.graphics.{SimpleTheme, Theme}
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.table.{DefaultTableCellRenderer, Table, TableCellRenderer, TableModel}
import com.googlecode.lanterna.input.KeyType

class ChatListView extends BaseView {
  private val window = BasicWindow("Select chat")
  private val panel = Panel(LinearLayout(Direction.VERTICAL))
  private val searchBox = TextBox(TerminalSize(30, 1))
  private val chatsModel = TableModel[String]("user name", "message")
  private val model = Seq(ChatRow("Alice", "hi"), ChatRow("Bob", "bye"))
  private val charBuffer = StringBuilder()

  var onChatSelect: String => Unit = _ => {}

  private val chatsTable = Table[String]("user name", "message")
    .setEscapeByArrowKey(true)
    .setTableModel(chatsModel)
    .setPreferredSize(TerminalSize(40, 10))

  private val searchPanel = Panel(LinearLayout(Direction.HORIZONTAL))
    .addComponent(
      Panel(LinearLayout(Direction.HORIZONTAL))
        .addComponent(Label("Search: "))
        .addComponent(searchBox).withBorder(Borders.singleLine())
    )

  panel.addComponent(searchPanel)
  panel.addComponent(Separator(Direction.HORIZONTAL))
  panel.addComponent(chatsTable.withBorder(Borders.singleLineBevel()))

  filterRows("")

  window.setComponent(panel)

  chatsTable.setSelectAction(() => {
    val selectedUser = chatsTable.getTableModel.getRow(chatsTable.getSelectedRow).get(0)
    window.close()
    onChatSelect(selectedUser)
  })

  searchBox.setInputFilter { (i, key) =>
    println(key.toString)
    key.getKeyType match {
      case KeyType.Character =>
        charBuffer.append(key.getCharacter.charValue())
        filterRows(charBuffer.toString())
      case KeyType.Backspace =>
        if (charBuffer.nonEmpty) {
          charBuffer.deleteCharAt(charBuffer.length() - 1)
        }

        filterRows(charBuffer.toString())
      case KeyType.Enter =>
        window.close()
        onChatSelect(charBuffer.toString())
      case _ =>
    }

    true
  }

  override def render(gui: MultiWindowTextGUI): Unit = {
    gui.addWindowAndWait(window)
  }

  private def filterRows(prefix: String): Unit = {
    chatsModel.clear()
    model
      .withFilter(_.userName.toLowerCase.startsWith(prefix.toLowerCase))
      .foreach { r => chatsModel.addRow(r.userName, r.lastMessage)}
  }
}

case class ChatRow(userName: String, lastMessage: String)