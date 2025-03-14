package org.chats
package view

import dto.{InputMessageDto, OutputMessageDto}

import scala.io.StdIn.readLine

class SimpleTextView {
  def displayMessage(message: OutputMessageDto): Unit = {
    println(message.text)
  }

  def readMessage(): InputMessageDto = InputMessageDto("whatever", "foo", readLine())

  def login(): String = {
    println("Welcome! Please enter your username")
    readLine()
  }
}
