package org.chats
package exception

sealed trait AuthError {
  val message: String
}

object AuthError {
  def unapply(err: AuthError): Option[String] = Some(err.message)
}

class InternalAuthError(val exception: Throwable, val message: String) extends AuthError {
  def this(exception: Throwable) = this(exception, exception.getMessage)
}

case class LoginFailure(message: String) extends AuthError
