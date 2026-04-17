package org.chats
package repository

import config.{DBSettings, Settings}
import context.dataSource
import db.MigrationManager
import model.{Session, User}

import com.dimafeng.testcontainers.PostgreSQLContainer
import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import org.scalactic.{Equality, Equivalence}
import org.scalatest.matchers.should.Matchers
import org.testcontainers.utility.DockerImageName
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertCompletes}
import zio.{Scope, ZIO, ZLayer}
import org.scalactic.Explicitly.*

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

val sessionEquivalence: Equality[Session] = (a, b) => {
  b match {
    case bS: Session =>
      val tupleA = Tuple.fromProductTyped(a)
      val tupleB = Tuple.fromProductTyped(bS)

      tupleA.zip(tupleB).toList.forall {
        case (arrA: Array[Byte], arrB: Array[Byte]) => arrA.sameElements(arrB)
        case (x, y) => x == y
      }
    case _ => false
  }
}

object SessionRepositoryTest extends ZIOSpecDefault with Matchers {
  val containerLayer = ZLayer.scoped {
    ZIO.acquireRelease(ZIO.attemptBlocking {
      val c = new PostgreSQLContainer(dockerImageNameOverride = Some(DockerImageName.parse("postgres:latest")))
      c.start()
      c
    })(c => ZIO.attemptBlocking(c.stop()).orDie)
  }

  val settingsLayer = ZLayer {
    ZIO.serviceWith[PostgreSQLContainer](c => Settings(DBSettings(
      url = c.jdbcUrl,
      username = c.username,
      password = c.password,
      schema = "auth"
    )))
  }

  val migrationLayer = ZLayer {
    for {
      s <- ZIO.service[Settings]
      _ <- MigrationManager(s.db).migrate().orDie
    } yield ()
  }

  override def spec: Spec[TestEnvironment & Scope, Any] = suite("SessionRepositoryTest") {
    test("should create a session") {
      // given
      val user = User(
        id = UUID.randomUUID().toString,
        username = "foo", password = sha256("bar"), createdAt = Instant.now()
      )

      val token = "abcde"
      val hash: Array[Byte] = sha256(token)

      val session = Session(
        id = UUID.randomUUID().toString,
        tokenHash = hash,
        userId = user.id,
        createdAt = Instant.now(),
        refreshedAt = Some(Instant.now()),
        expiresIn = 3000
      )
      // when
      for {
        _ <- ZIO.serviceWithZIO[UserRepository](_.create(user))
        _ <- ZIO.serviceWithZIO[SessionRepository](_.create(session))
        loaded <- ZIO.serviceWithZIO[SessionRepository](_.getSessionByTokenHash(hash))
      } yield {
        (loaded.get should equal (session)) (decided by sessionEquivalence)
        assertCompletes
        //assertTrue(loaded == Some(session))
      }
    }
      .provide(
        containerLayer,
        settingsLayer,
        migrationLayer,
        dataSource,
        Quill.Postgres.fromNamingStrategy(SnakeCase),
        ZLayer.fromFunction(SessionRepositoryImpl(_)),
        ZLayer.fromFunction(UserRepositoryImpl(_))
      )
  }

  private def sha256(token: String): Array[Byte] = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(token.getBytes(StandardCharsets.UTF_8))
  }
}
