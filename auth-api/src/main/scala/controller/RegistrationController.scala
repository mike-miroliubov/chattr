package org.chats
package controller

import exception.{AuthError, InternalAuthError}
import org.chats.service.RegistrationService
import zio.ZIO
import zio.http.*
import zio.json.*

case class RegistrationRequest(username: String, password: String)
given decoder: JsonDecoder[RegistrationRequest] = DeriveJsonDecoder.gen

case class RegistrationResponse(username: String)
given encoder: JsonEncoder[RegistrationResponse] = DeriveJsonEncoder.gen

val registrationRoutes: Routes[RegistrationService, Nothing] = Routes(
  Method.POST / "register" -> Handler.fromFunctionHandler { (req: Request) =>
    val response = for {
      request <- req.body.asJsonFromCodec[RegistrationRequest].mapError(e => new IllegalArgumentException(e.getMessage, e))
      user <- ZIO.serviceWithZIO[RegistrationService](_.register(request.username, request.password))
    } yield {
      Response.json(RegistrationResponse(user.username).toJson)
    }

    Handler.fromZIO(response.tapDefect(e => ZIO.logCause(e))).catchAll {
      case _: IllegalArgumentException => errorHandler("Invalid request parameters", Status.BadRequest)
      case _: InternalAuthError => errorHandler("Internal error", Status.InternalServerError)
      case AuthError(message) => errorHandler(message, Status.BadRequest)
    }
  }
)
