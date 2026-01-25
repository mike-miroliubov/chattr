package org.chats
package context

import org.chats.config.{Settings, SettingsConfig}
import org.chats.service.{LoginService, LoginServiceImpl, RegistrationService}
import zio.{ZIO, ZLayer}
import config.SettingsConfig.given

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.chats.repository.{UserRepository, UserRepositoryImpl}

val userRepository = ZLayer.fromFunction(UserRepositoryImpl(_))
val loginService: ZLayer[UserRepository, Nothing, LoginService] = ZLayer {
  ZIO.serviceWith[UserRepository](LoginServiceImpl(_))
}

val registrationService: ZLayer[UserRepository, Nothing, RegistrationService] = ZLayer {
  ZIO.serviceWith[UserRepository](RegistrationService(_))
}

val dataSource = ZLayer {
  ZIO.config[Settings].map(s => {
    val config = new HikariConfig()
    config.setSchema(s.db.schema)
    config.setJdbcUrl(s.db.url)
    config.setUsername(s.db.username)
    config.setPassword(s.db.password)

    // TODO: configure connection pool settings

    HikariDataSource(config)
  })
}