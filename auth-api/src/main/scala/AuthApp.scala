package org.chats

import org.chats.context.{loginController, loginService}
import org.chats.routes
import zio.http.Server
import zio.{ZIO, ZIOAppDefault}

object AuthApp extends ZIOAppDefault {
  override def run: ZIO[Any, Throwable, Nothing] =
    Server
      .serve(routes)
      .provide(
        Server.default,
        loginController,
        loginService
      )
}