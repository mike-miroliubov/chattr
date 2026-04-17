package org.chats
package db

import config.DBSettings

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.output.MigrateResult
import zio.{IO, ZIO}

import scala.jdk.CollectionConverters.given

class MigrationManager(val dBSettings: DBSettings) {
  def migrate(): IO[FlywayException, MigrateResult] = {
    val flyway = Flyway.configure()
      .dataSource(dBSettings.url, dBSettings.username, dBSettings.password)
      .schemas(dBSettings.schema)
      .defaultSchema(dBSettings.schema)
      .createSchemas(true)
      .load()

    ZIO.attemptBlocking { flyway.migrate() }
      .refineToOrDie[FlywayException]
      .tap(r => {
        r.migrations.asScala match {
          case b if b.isEmpty => ZIO.log("No migrations to apply")
          case b => ZIO.foreach(b)(m => ZIO.log(s"Applied migration ${m.version}"))
        }
      })
  }
}
