package org.chats
package config

import pureconfig._

case class Settings(
  cassandra: CassandraSettings
) derives ConfigReader
case class CassandraSettings(
  keyspace: String,
  preparedStatementCacheSize: Int,
  session: SessionSettings
)

case class QueryOptions(
  consistencyLevel: String
)

case class SessionSettings(
  contactPoint: List[String],
  localDatacenter: String,
  port: Int,
  queryOptions: QueryOptions,
  withoutMetrics: Boolean,
  withoutJMXReporting: String,
  authProvider: AuthProviderSettings,
  maxSchemaAgreementWaitSeconds: Int,
  addressTranslator: String
)

case class AuthProviderSettings(
  providerClass: String,
  userName: String,
  password: String,
)


