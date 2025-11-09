package org.chats
package config

import repository.{CassandraChatRepository, CassandraMessageRepository}
import service.ChatServiceImpl

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.{DefaultDriverOption, DriverConfigLoader}
import io.getquill.util.LoadConfig
import io.getquill.{CassandraAsyncContext, SnakeCase}
import org.chats.controller.ChatController
import pureconfig.ConfigSource

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext.Implicits.global

val config = LoadConfig("ctx")
val settings = ConfigSource.default.loadOrThrow[Settings]
val cassandraContext: CassandraAsyncContext[SnakeCase] = buildCassandraConnection()

val chatRepository = CassandraChatRepository(cassandraContext)
val messageRepository = CassandraMessageRepository(cassandraContext)

val chatService = ChatServiceImpl(chatRepository, messageRepository)

val chatController = ChatController(chatService)

def buildCassandraConnection() = {
  val cassandraSettings = settings.cassandra
  val sessionBuilder = CqlSession.builder().withConfigLoader(DriverConfigLoader.programmaticBuilder()
    .withString(DefaultDriverOption.REQUEST_CONSISTENCY, settings.cassandra.session.queryOptions.consistencyLevel)
    .build())

  cassandraSettings.session.contactPoint.foreach { it =>
    sessionBuilder.addContactPoint(new InetSocketAddress(it, cassandraSettings.session.port))
  }

  sessionBuilder
    .withLocalDatacenter(cassandraSettings.session.localDatacenter)
    .withKeyspace(cassandraSettings.keyspace)
    .withAuthCredentials(cassandraSettings.session.authProvider.userName, cassandraSettings.session.authProvider.password)

  new CassandraAsyncContext(SnakeCase, sessionBuilder.build(), 1000)
}
