package org.chats
package view

import com.googlecode.lanterna.{TerminalSize, TextColor}
import com.googlecode.lanterna.graphics.{SimpleTheme, Theme}
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.table.{DefaultTableCellRenderer, Table, TableCellRenderer, TableModel}

class ChatListView extends BaseView {
  private val window = BasicWindow("Select chat")
  private val panel = Panel(LinearLayout(Direction.VERTICAL))
  private val searchBox = TextBox(TerminalSize(30, 1))
  private val chatsModel = TableModel[String]("user name", "message")

  var onChatSelect: String => Unit = _ => {}

  private val chatsTable = Table[String]("user name", "message")
    .setEscapeByArrowKey(true)
    .setTableModel(chatsModel)
    .setPreferredSize(TerminalSize(40, 10))

  chatsTable.setSelectAction(() => {
    val selectedUser = chatsTable.getTableModel.getRow(chatsTable.getSelectedRow).get(0)
    window.close()
    onChatSelect(selectedUser)
  })

  private val searchPanel = Panel(LinearLayout(Direction.HORIZONTAL))
    .addComponent(
      Panel(LinearLayout(Direction.HORIZONTAL))
        .addComponent(Label("Search: "))
        .addComponent(searchBox).withBorder(Borders.singleLine())
    )

  panel.addComponent(searchPanel)
  panel.addComponent(Separator(Direction.HORIZONTAL))
  panel.addComponent(chatsTable.withBorder(Borders.singleLineBevel()))

  chatsModel.addRow("Alice", "hi")
  chatsModel.addRow("Bob", "bye")

  window.setComponent(panel)

  override def render(gui: MultiWindowTextGUI): Unit = {
    gui.addWindowAndWait(window)
  }
}