package org.chats
package controller

import exception.AuthError
import service.LoginService

import zio.ZIO
import zio.http.*
import zio.json.*

case class LoginRequest(username: String, password: String)
private given decoder: JsonDecoder[LoginRequest] = DeriveJsonDecoder.gen[LoginRequest]

case class LoginResponse(sessionToken: String, accessToken: String)
private given encoder: JsonEncoder[LoginResponse] = DeriveJsonEncoder.gen

val authRoutes: Routes[LoginService, Nothing] = Routes(
  Method.POST / "login" -> handle { (req: Request) =>
    val response = for {
      request <- req.body.asJsonFromCodec[LoginRequest].mapError(e => new IllegalArgumentException(e.getMessage, e))
      sessionToken <- ZIO.serviceWithZIO[LoginService](_.login(request.username, request.password))
    } yield {
      Response.json(LoginResponse(sessionToken, "access").toJson)
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