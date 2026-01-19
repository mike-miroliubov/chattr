package org.chats.controller

import org.chats.exception.AuthError
import org.chats.service.LoginService
import zio.{IO, UIO}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class LoginRequest(username: String, password: String)
given decoder: JsonDecoder[LoginRequest] = DeriveJsonDecoder.gen[LoginRequest]

case class LoginResponse(sessionToken: String, accessToken: String)
given encoder: JsonEncoder[LoginResponse] = DeriveJsonEncoder.gen

trait LoginController {
  def login(form: LoginRequest): IO[AuthError, LoginResponse]
}

class LoginControllerImpl(loginService: LoginService) extends LoginController {
  override def login(request: LoginRequest): IO[AuthError, LoginResponse] =
    loginService.login(request.username, request.password).map { session => LoginResponse(session.token, "access") }
}