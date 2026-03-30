package org.chats
package context

import org.chats.config.{Settings, SettingsConfig}
import org.chats.service.{LoginService, LoginServiceImpl, RegistrationService, SessionService, SessionServiceImpl}
import zio.{ZIO, ZLayer}
import config.SettingsConfig.given

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.chats.repository.{UserRepository, UserRepositoryImpl}

val userRepository = ZLayer.fromFunction(UserRepositoryImpl(_))
val sessionService = ZLayer.succeed(SessionServiceImpl())

val loginService: ZLayer[UserRepository & SessionService, Nothing, LoginService] = ZLayer {
    for {
      repo <- ZIO.service[UserRepository]
      sessionService <- ZIO.service[SessionService]
    } yield LoginServiceImpl(repo, sessionService)
  }

val registrationService: ZLayer[UserRepository, Nothing, RegistrationService] = ZLayer {
  ZIO.serviceWith[UserRepository](RegistrationService(_))
}

val settings = ZLayer { ZIO.config[Settings] }

val dataSource = ZLayer {
  ZIO.serviceWith[Settings] { s =>
    val config = new HikariConfig()
    config.setSchema(s.db.schema)
    config.setJdbcUrl(s.db.url)
    config.setUsername(s.db.username)
    config.setPassword(s.db.password)

    // TODO: configure connection pool settings

    HikariDataSource(config)
  }
}