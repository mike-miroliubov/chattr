package org.chats
package view

import com.googlecode.lanterna.gui2.MultiWindowTextGUI

trait BaseView {
  def render(gui: MultiWindowTextGUI): Unit
}
