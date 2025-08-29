package org.chats
package repository

import com.dimafeng.testcontainers.CassandraContainer
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import org.chats.config.customizeConfigWithEnvironment
import org.chats.service.ClientActor.IncomingMessage
import org.scalatest.flatspec.AsyncFlatSpec

import java.util.UUID

class MessageRepositoryTest extends AsyncFlatSpec {
  private val containerDef = CassandraContainer.Def(initScript = Some("migrations/messages.cql"))
  private val container = containerDef.createContainer()
  container.start()

  private val config: Config = customizeConfigWithEnvironment("application-repository-test.conf", Map(
    "datastax-java-driver.basic.contact-points" -> s"[\"127.0.0.1:${container.mappedPort(9042)}\"]",
    "datastax-java-driver.basic.load-balancing-policy.local-datacenter" -> container.cassandraContainer.getLocalDatacenter
  ))
  val testSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system", config)
  val cassandraSession: CassandraSession = CassandraSessionRegistry.get(testSystem).sessionFor(CassandraSessionSettings())


  "Repository" should "save message" in {
    val repo = MessageRepositoryImpl(cassandraSession, testSystem)
    val message = IncomingMessage(UUID.randomUUID().toString, "hello", "world", "scala")

    for {
      saved <- repo.save(message)
      loaded <- repo.findChatMessages("world")
    } yield {
      assert(saved == message)
      assert(loaded == Seq(message))
    }
  }
}
