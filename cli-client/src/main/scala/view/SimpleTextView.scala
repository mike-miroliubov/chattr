package org.chats
package view

import dto.{OutputMessageDto, InputMessageDto}

import java.util.UUID
import scala.io.StdIn.readLine

sealed trait ViewCommand {
  def execute(): Unit
}

class SimpleTextView {
  private class ChangeChatCommand(val chatId: String) extends ViewCommand {
    override def execute(): Unit = SimpleTextView.this.chatId = Some(chatId)
  }

  private var chatId: Option[String] = None

  def displayMessage(message: InputMessageDto): Unit = {
    println(s"${message.from}: ${message.text}")
  }

  def displayNote(note: String): Unit = {
    println(s"--- $note ---")
  }

  def readMessage(): Option[OutputMessageDto] = {
    parseCommand(readLine()) match {
      case text: String => chatId.map(OutputMessageDto(UUID.randomUUID().toString, _, text))
      case cmd: ViewCommand =>
        cmd.execute()
        None
    }
  }

  private def parseCommand(str: String): ViewCommand | String = str match {
    case s"\\w $username" => ChangeChatCommand(username)
    case s"\\with $username" => ChangeChatCommand(username)
    case _ => str
  }

  def login(): String = {
    println("Welcome! Please enter your username")
    readLine()
  }
}
