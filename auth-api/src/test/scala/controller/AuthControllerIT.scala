package org.chats
package controller

import model.{Session, User}
import service.{LoginService, RegistrationService}

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.*
import zio.http.*
import zio.test.*

import java.time.Instant

object AuthControllerIT extends ZIOSpecDefault {
  val mockLoginService = ZLayer[Any, Nothing, LoginService] {
    ZIO.succeed(mock[LoginService])
  }

  val mockRegistrationService = ZLayer[Any, Nothing, RegistrationService] {
    ZIO.succeed(mock[RegistrationService])
  }

  override def spec = suite("AuthControllerIT") {
    test("should login with valid username and password") {
      for {
        client <- ZIO.service[Client]
        port <- ZIO.serviceWithZIO[Server](_.port)

        testRequest = Request
          .get(url = URL.root.port(port))
          .addHeaders(Headers(Header.Accept(MediaType.application.json)))

        _ <- TestServer.addRoutes(authRoutes ++ registrationRoutes)
        _ <- ZIO.serviceWith[LoginService] { mock =>
          when(mock.login(any(), any())).thenReturn(ZIO.succeed(Session("", "token", User("", "", Array(), Instant.now()))))
        }

        loginResponse <- client.batched(Request.post(testRequest.url / "login", Body.fromString("{\"username\": \"kite\",\"password\": \"foo\"}")))
        loginResponseBody <- loginResponse.body.asString
      } yield {
        assertTrue(loginResponse.status == Status.Ok)
        assertTrue(loginResponseBody == "{\"sessionToken\":\"token\",\"accessToken\":\"access\"}")
      }
    }
  }.provide(
    TestServer.default,
    Client.default,
    mockLoginService,
    mockRegistrationService
  )
}