package org.chats
package controller

import exception.{AuthError, InternalAuthError}
import service.LoginService

import zio.ZIO
import zio.http.*
import zio.json.*

case class LoginRequest(username: String, password: String)
given decoder: JsonDecoder[LoginRequest] = DeriveJsonDecoder.gen[LoginRequest]

case class LoginResponse(sessionToken: String, accessToken: String)
given encoder: JsonEncoder[LoginResponse] = DeriveJsonEncoder.gen

case class APIError(message: String)
given errorEncoder: JsonEncoder[APIError] = DeriveJsonEncoder.gen

def errorHandler(message: String, status: Status) = {
  Handler.fromResponse {
    Response
      .json(APIError(message).toJson)
      .status(status)
  }
}

val authRoutes: Routes[LoginService, Nothing] = Routes(
  Method.POST / "login" -> Handler.fromFunctionHandler { (req: Request) =>
    val response = for {
      request <- req.body.asJsonFromCodec[LoginRequest].mapError(e => new IllegalArgumentException(e.getMessage, e))
      session <- ZIO.serviceWithZIO[LoginService](_.login(request.username, request.password))
    } yield {
      Response.json(LoginResponse(session.token, "access").toJson)
    }

    Handler.fromZIO(response.tapDefect(e => ZIO.logCause(e))).catchAll {
      case _: IllegalArgumentException => errorHandler("Invalid request parameters", Status.BadRequest)
      case _: InternalAuthError => errorHandler("Internal error", Status.InternalServerError)
      case AuthError(message) => errorHandler(message, Status.BadRequest)
    }
  }
)