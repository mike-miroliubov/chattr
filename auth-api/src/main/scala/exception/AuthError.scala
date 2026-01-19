package org.chats
package exception

trait AuthError {
  def message: String
}

class InternalError(val exception: Throwable, val msg: String) extends AuthError {
  override def message: String = msg

  def this(exception: Throwable) = this(exception, exception.getMessage)
}
