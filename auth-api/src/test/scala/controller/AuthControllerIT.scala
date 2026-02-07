package org.chats
package controller

import config.{DBSettings, Settings}
import context.{dataSource, loginService, registrationService, userRepository}
import db.MigrationManager
import service.{LoginService, RegistrationService}

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.zaxxer.hikari.HikariDataSource
import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import zio.*
import zio.http.*
import zio.test.*

import javax.sql.DataSource

object AuthControllerIT extends ZIOSpecDefault {

  val containerLayer = ZLayer.scoped {
    ZIO.acquireRelease(ZIO.attemptBlocking {
      val c = new PostgreSQLContainer()
      c.start()
      c
    })(c => ZIO.attemptBlocking(c.stop()).orDie)
  }

  val settingsLayer = ZLayer {
    ZIO.serviceWith[PostgreSQLContainer](c => Settings(DBSettings(
      url = c.jdbcUrl,
      username = c.username,
      password = c.password,
      schema = "auth"
    )))
  }

  val migrationLayer = ZLayer {
    for {
      s <- ZIO.service[Settings]
      _ <- MigrationManager(s.db).migrate().orDie
    } yield ()
  }

  override def spec = suite("AuthControllerIT") {
    test("should register and then login with valid username and password") {
      for {
        client <- ZIO.service[Client]
        port <- ZIO.serviceWithZIO[Server](_.port)

        baseUrl = URL.root.port(port)

        _ <- TestServer.addRoutes(authRoutes ++ registrationRoutes)

        // 1. Register
        registerResponse <- client.batched(
          Request.post(
            baseUrl / "register",
            Body.fromString("{\"username\": \"kite\",\"password\": \"foo\"}")
          )
        )

        // 2. Login
        loginResponse <- client.batched(
          Request.post(
            baseUrl / "login",
            Body.fromString("{\"username\": \"kite\",\"password\": \"foo\"}")
          )
        )
        loginResponseBody <- loginResponse.body.asString
      } yield {
        assertTrue(registerResponse.status == Status.Ok)
        assertTrue(loginResponse.status == Status.Ok)
        assertTrue(loginResponseBody.contains("sessionToken"))
        assertTrue(loginResponseBody.contains("accessToken"))
      }
    }
  }.provide(
    TestServer.default,
    Client.default,
    containerLayer,
    settingsLayer,
    dataSource,
    migrationLayer,
    Quill.Postgres.fromNamingStrategy(SnakeCase),
    userRepository,
    loginService,
    registrationService
  )
}