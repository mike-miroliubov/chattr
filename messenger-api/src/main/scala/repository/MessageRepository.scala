package org.chats
package repository

import service.ClientActor.IncomingMessage

import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.{CassandraFlow, CassandraSession, CassandraSource}
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

trait MessageRepository {
  def save(msg: IncomingMessage): Future[IncomingMessage]
  def findChatMessages(chatId: String): Future[Seq[IncomingMessage]]
}

class MessageRepositoryImpl(
                             cassandraSession: CassandraSession,
                             actorSystem: ActorSystem[_]
                           ) extends MessageRepository {
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

  override def findChatMessages(chatId: String): Future[Seq[IncomingMessage]] =
    CassandraSource("""SELECT * FROM chattr.message WHERE chat_id = ?""", chatId)
      .map(row => IncomingMessage(
        row.getString("message_id"),
        row.getString("message"),
        chatId,
        row.getString("from_user_id")))
      .runWith(Sink.seq)
}

private val statementBinder = (msg: IncomingMessage, stmt: PreparedStatement) => stmt.bind(msg.to, msg.messageId, msg.from, msg.text)

