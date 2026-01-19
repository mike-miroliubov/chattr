package org.chats
package context

import org.chats.controller.{LoginController, LoginControllerImpl}
import org.chats.service.{LoginService, LoginServiceImpl}
import zio.{ZIO, ZLayer}

val loginService: ZLayer[Any, Nothing, LoginService] = ZLayer { ZIO.succeed(LoginServiceImpl()) }
val loginController: ZLayer[LoginService, Nothing, LoginController] = ZLayer {
  ZIO.serviceWith[LoginService](service => LoginControllerImpl(service))
}
