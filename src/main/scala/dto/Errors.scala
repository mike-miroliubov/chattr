package org.chats
package dto

final case class ApiError(message: String, code: String)

object Errors {
  val ObjectNotFoundError = ApiError("Requested object was not found", "400-01")
}
