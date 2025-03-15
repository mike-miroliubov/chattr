package org.chats
package view

import dto.{InputMessageDto, OutputMessageDto}

import java.util.UUID
import scala.io.StdIn.readLine

sealed trait ViewCommand {
  def execute(): Unit
}

class SimpleTextView {
  private class ChangeChatCommand(val chatId: String) extends ViewCommand {
    override def execute(): Unit = {
      SimpleTextView.this.chatId = Some(chatId)
    }
  }

  private var chatId: Option[String] = None
  def displayMessage(message: OutputMessageDto): Unit = {
    println(s"${message.from}: ${message.text}")
  }

  def displayNote(note: String): Unit = {
    println(s"--- $note ---")
  }

  def readMessage(): Option[InputMessageDto] = {
    _parseCommand(readLine()) match {
      case text: String => chatId.map(InputMessageDto(UUID.randomUUID().toString, _, text))
      case cmd: ViewCommand =>
        cmd.execute()
        None
    }
  }

  def _parseCommand(str: String): ViewCommand | String = str match {
    case s"\\w $username" => ChangeChatCommand(username)
    case s"\\with $username" => ChangeChatCommand(username)
    case _ => str
  }

  def login(): String = {
    println("Welcome! Please enter your username")
    readLine()
  }
}
