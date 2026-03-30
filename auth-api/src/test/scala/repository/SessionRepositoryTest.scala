package org.chats
package repository

import config.{DBSettings, Settings}

import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import org.chats.context.dataSource
import org.chats.db.MigrationManager
import org.chats.model.{Session, User}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Scope, ZIO, ZLayer}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

object SessionRepositoryTest extends ZIOSpecDefault {
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
        assertTrue(loaded == session)
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
