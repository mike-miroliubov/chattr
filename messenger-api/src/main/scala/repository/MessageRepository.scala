package org.chats
package repository

import service.ClientActor.IncomingMessage

import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.{CassandraFlow, CassandraSession}
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

class MessageRepository(
                             cassandraSession: CassandraSession,
                             actorSystem: ActorSystem[_]
                           ) {
  given session: CassandraSession = cassandraSession
  given system: ActorSystem[_] = actorSystem

  private val saveFlow = Flow[IncomingMessage].via(CassandraFlow.create(CassandraWriteSettings.defaults,
    """INSERT INTO chattr.message(chat_id,
    message_id,
    from_user_id,
    message) VALUES (?,?,?,?)""", statementBinder))

  def save(msg: IncomingMessage): Future[IncomingMessage] =
    Source.single(msg)
    .via(saveFlow)
    .runWith(Sink.head)
}

private val statementBinder = (msg: IncomingMessage, stmt: PreparedStatement) => stmt.bind(msg.to, msg.messageId, msg.from, msg.text)

