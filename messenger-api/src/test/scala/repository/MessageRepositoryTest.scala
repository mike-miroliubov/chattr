package org.chats
package repository

import model.ChattrMessage

import com.dimafeng.testcontainers.CassandraContainer
import com.typesafe.config.Config
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import org.chats.settings.initConfig
import org.scalactic.Equality
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers.*

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.jdk.CollectionConverters.given

class MessageRepositoryTest extends AsyncFlatSpec {
  private val containerDef = CassandraContainer.Def(initScript = Some("migrations/messages.cql"))
  private val container = containerDef.createContainer()
  container.start()

  private val config: Config = initConfig("application-repository-test.conf", Map(
    "datastax-java-driver.basic.contact-points" -> List(s"127.0.0.1:${container.mappedPort(9042)}").asJava,
    "datastax-java-driver.basic.load-balancing-policy.local-datacenter" -> container.cassandraContainer.getLocalDatacenter
  ))
  val testSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system", config)
  val cassandraSession: CassandraSession = CassandraSessionRegistry.get(testSystem).sessionFor(CassandraSessionSettings())

  given dateTimeEquality: Equality[LocalDateTime] = (a: LocalDateTime, b: Any) => b match {
    // Compare with millisecond tolerance
    case otherDateTime: LocalDateTime => a.until(otherDateTime, ChronoUnit.MILLIS) == 0
    case _ => false
  }

  given messageEquality: Equality[ChattrMessage] = (a: ChattrMessage, b: Any) => b match {
    case otherMessage: ChattrMessage =>
      val t1 = Tuple.fromProductTyped(a).toList
      val t2 = Tuple.fromProductTyped(otherMessage).toList
      t1.zip(t2).forall { (x, y) =>
          x match {
            case dtX: LocalDateTime =>
              dtX === y   // Now Scala know the types and can pick up the right Equality instance!
            case _ => x === y
          }
        }
    case _ => false
  }


  "Repository" should "save message" in {
    val repo = MessageRepositoryImpl(cassandraSession, testSystem)
    val message = ChattrMessage("scala#world", UUID.randomUUID().toString, UUID.randomUUID().toString, "scala", "hello",
      LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC), None)  // Some(LocalDateTime.now(ZoneOffset.UTC))

    for {
      saved <- repo.save(message)
      loaded <- repo.findChatMessages("scala#world")
    } yield {
      assert(saved == message)
      loaded should contain only message
    }
  }

  "Repository" should "update inbox" in {
    val repo = MessageRepositoryImpl(cassandraSession, testSystem)
    val message = ChattrMessage(s"scala#world", UUID.randomUUID().toString, "", "scala", "hello",
      LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC), Some(LocalDateTime.now(ZoneOffset.UTC)))

    for {
      _ <- repo.updateInbox("scala", message)
      loaded <- repo.loadInbox("scala")
    } yield {
      assert(loaded.head.messageId == message.messageId)
    }
  }
}
