package org.chats
package view

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyType

class LoginView extends BaseView {
  private val window = BasicWindow("Please log in")
  private val loginInput = TextBox(TerminalSize(20, 1))
  var onLogin: String => Unit = _ => {}

  // main layout
  window.setComponent(
    Panel(LinearLayout(Direction.VERTICAL))
      .addComponent(loginInput.withBorder(Borders.singleLine("Login"))))

  loginInput.setInputFilter { (_, keyStroke) => keyStroke.getKeyType match {
    case KeyType.Enter =>
      window.close()
      onLogin(loginInput.getText.strip())
      false
    case _ => true
  }}

  override def render(gui: MultiWindowTextGUI): Unit = {
    loginInput.takeFocus()
    gui.addWindowAndWait(window)
  }
}
