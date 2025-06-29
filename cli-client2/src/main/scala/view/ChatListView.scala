package org.chats
package view

import com.googlecode.lanterna.gui2.*

class ChatListView extends BaseView {
  private val window = BasicWindow("Select chat")
  private val panel = Panel(LinearLayout(Direction.VERTICAL))

  var onChatSelect: String => Unit = _ => {}

  Seq("Alice", "Bob").foreach { userName =>
    panel.addComponent(Button(userName, () => {
      window.close()
      onChatSelect(userName)
    }))
  }

  window.setComponent(panel)

  override def render(gui: MultiWindowTextGUI): Unit = {
    gui.addWindowAndWait(window)
  }
}
