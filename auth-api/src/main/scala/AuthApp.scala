package org.chats

import config.Settings
import config.SettingsConfig.given
import context.{dataSource, loginService, userRepository}
import db.MigrationManager

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import org.chats.controller.authRoutes
import zio.http.Server
import zio.{ZIO, ZIOAppDefault}

object AuthApp extends ZIOAppDefault {
  override def run: ZIO[Any, Throwable, Nothing] = {
    for {
      config <- ZIO.config[Settings].tap { s => ZIO.attempt(Console.println(s)) }
      _ <- MigrationManager(config.db).migrate()
      server <- Server
        .serve(authRoutes)
        .provide(
          Server.default,
          dataSource,
          Quill.Postgres.fromNamingStrategy(SnakeCase),
          userRepository,
          //loginController,
          loginService
        )
    } yield server
  }
}