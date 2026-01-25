package org.chats

import controller.{APIError, LoginController, LoginRequest, given}
import exception.{AuthError, InternalError}

import zio.ZIO
import zio.http.*
import zio.json.*

def errorHandler(message: String, status: Status) = {
  Handler.fromResponse {
    Response
      .json(APIError(message).toJson)
      .status(status)
  }
}

val routes: Routes[LoginController, Nothing] = Routes(
  Method.POST / "login" -> Handler.fromFunctionHandler { (req: Request) =>
    val response = for {
      request <- req.body.asJsonFromCodec[LoginRequest].mapError(e => new IllegalArgumentException(e.getMessage, e))
      result <- ZIO.serviceWithZIO[LoginController](_.login(request))
    } yield {
      Response.json(result.toJson)
    }

    Handler.fromZIO(response.tapDefect(e => ZIO.logCause(e))).catchAll {
      case _: IllegalArgumentException => errorHandler("Invalid request parameters", Status.BadRequest)
      case _: InternalError => errorHandler("Internal error", Status.InternalServerError)
      case AuthError(message) => errorHandler(message, Status.BadRequest)
    }
  }
)