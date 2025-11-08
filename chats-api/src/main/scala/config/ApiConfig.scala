package org.chats
package config

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.config.{DefaultDriverOption, DriverConfigLoader}
import io.getquill.util.LoadConfig
import io.getquill.{CassandraAsyncContext, SnakeCase}
import org.chats.service.ChatServiceImpl
import org.chats.repository.{CassandraChatRepository, InMemoryRepository}
import pureconfig.ConfigSource

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext.Implicits.global

lazy val repository = InMemoryRepository()
val config = LoadConfig("ctx")
val settings = ConfigSource.default.loadOrThrow[Settings]

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
lazy val cassandraContext: CassandraAsyncContext[SnakeCase] = buildCassandraConnection()

lazy val chatRepository = CassandraChatRepository(cassandraContext)

lazy val chatService = ChatServiceImpl(chatRepository, repository)
