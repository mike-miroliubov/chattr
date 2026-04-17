package org.chats
package controller

import exception.InternalAuthError

import zio.ZIO
import zio.http.{Handler, Request, Response, Status}
import zio.json.*

case class APIError(message: String)
given errorEncoder: JsonEncoder[APIError] = DeriveJsonEncoder.gen

def errorHandler(message: String, status: Status) = {
  Handler.fromResponse {
    Response
      .json(APIError(message).toJson)
      .status(status)
  }
}

/**
 * A common handler that encapsulates common error handling logic
 */
def handle[R](handler: Request => ZIO[R, Any, Response]): Handler[R, Nothing, Request, Response] = Handler.fromFunctionHandler { (req: Request) =>
  val response = handler(req)

  Handler.fromZIO(
      response.tapDefect(e => ZIO.logCause(e)) // log all fatal errors
    )
    .catchAll {
      case _: IllegalArgumentException => errorHandler("Invalid request parameters", Status.BadRequest)
      case _: InternalAuthError => errorHandler("Internal error", Status.InternalServerError)
    }
    .catchAllDefect(_ => errorHandler("Internal error", Status.InternalServerError))
}