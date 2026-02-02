package org.chats
package controller

import exception.AuthError
import service.RegistrationService

import zio.ZIO
import zio.http.*
import zio.json.*

case class RegistrationRequest(username: String, password: String)

private given decoder: JsonDecoder[RegistrationRequest] = DeriveJsonDecoder.gen

case class RegistrationResponse(username: String)

private given encoder: JsonEncoder[RegistrationResponse] = DeriveJsonEncoder.gen

val registrationRoutes: Routes[RegistrationService, Nothing] = Routes(
  Method.POST / "register" -> handle { req =>
    val response = for {
      request <- req.body.asJsonFromCodec[RegistrationRequest].mapError(e => new IllegalArgumentException(e.getMessage, e))
      user <- ZIO.serviceWithZIO[RegistrationService](_.register(request.username, request.password))
    } yield {
      Response.json(RegistrationResponse(user.username).toJson)
    }

    response.catchSome {
      case AuthError(message) => ZIO.succeed(
        Response
          .json(APIError(message).toJson)
          .status(Status.BadRequest)
      )
    }
  }
)
