package org.chats

import controller.{LoginController, LoginRequest, given}
import exception.{AuthError, InternalError}

import zio.ZIO
import zio.http.*
import zio.json.*

val routes: Routes[LoginController, Nothing] = Routes(
  Method.POST / "login" -> Handler.fromFunctionHandler { (req: Request) =>
    val response = for {
      request <- req.body.asJsonFromCodec[LoginRequest].mapError(e => new IllegalArgumentException(e.getMessage, e))
      controller <- ZIO.service[LoginController]
      result <- controller.login(request)
    } yield {
      Response.json(result.toJson)
    }

    Handler.fromZIO(response).catchAll {
      case _: IllegalArgumentException => Handler.badRequest
      case _: InternalError => Handler.internalServerError
      case _: AuthError  => Handler.badRequest
    }
  }
)
