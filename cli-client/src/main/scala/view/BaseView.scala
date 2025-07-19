package org.chats
package view

import com.googlecode.lanterna.{SGR, TextColor}
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.table.Table

trait BaseView {
  def render(gui: MultiWindowTextGUI): Unit
}

val THEME = {
  val t = SimpleTheme.makeTheme(
    true,
    TextColor.ANSI.WHITE,
    TextColor.ANSI.BLACK,
    TextColor.ANSI.YELLOW_BRIGHT,
    TextColor.ANSI.BLACK,
    TextColor.ANSI.CYAN_BRIGHT,
    TextColor.ANSI.BLACK,
    TextColor.ANSI.BLACK
  )

  t.getDefinition(classOf[Table[_]]).setCustom("HEADER", TextColor.ANSI.WHITE, TextColor.ANSI.BLACK, SGR.BOLD, SGR.UNDERLINE)

  t
}