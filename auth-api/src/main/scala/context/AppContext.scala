package org.chats
package context

import org.chats.config.{DBSettings, Settings}
import org.chats.controller.{LoginController, LoginControllerImpl}
import org.chats.db.MigrationManager
import org.chats.service.{LoginService, LoginServiceImpl}
import zio.{ZIO, ZLayer}
import config.SettingsConfig.given

val loginService: ZLayer[Any, Nothing, LoginService] = ZLayer { ZIO.succeed(LoginServiceImpl()) }
val loginController: ZLayer[LoginService, Nothing, LoginController] = ZLayer {
  ZIO.serviceWith[LoginService](service => LoginControllerImpl(service))
}