package org.chats
package repository

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import org.chats.service.ClientActor.IncomingMessage
import org.scalatest.flatspec.AsyncFlatSpec

import java.util.UUID

class MessageRepositoryTest extends AsyncFlatSpec {
  val testSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system", ConfigFactory.load("application-repository-test.conf"))
  val cassandraSession: CassandraSession = CassandraSessionRegistry.get(testSystem).sessionFor(CassandraSessionSettings())


  "Repository" should "save message" in {
    val repo = MessageRepositoryImpl(cassandraSession, testSystem)
    val message = IncomingMessage(UUID.randomUUID().toString, "hello", "world", "scala")
    repo.save(message).map { it =>
      assert(it == message)
    }
  }
}
