package org.chats

import config.Settings
import config.SettingsConfig.given
import context.{loginController, loginService}
import db.MigrationManager

import org.chats.routes
import zio.http.Server
import zio.{ZIO, ZIOAppDefault}

object AuthApp extends ZIOAppDefault {
  override def run: ZIO[Any, Throwable, Nothing] = {
    for {
      config <- ZIO.config[Settings].tap { s => ZIO.attempt(Console.println(s)) }
      _ <- MigrationManager(config.db).migrate()
      server <- Server
        .serve(routes)
        .provide(
          Server.default,
          loginController,
          loginService
        )
    } yield server
  }
}