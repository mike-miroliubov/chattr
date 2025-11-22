package org.chats
package repository

import model.ChattrMessage

import com.datastax.oss.driver.api.core.cql.PreparedStatement
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.{CassandraFlow, CassandraSession, CassandraSource}
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.Future

trait MessageRepository {
  def save(msg: ChattrMessage): Future[ChattrMessage]
  def findChatMessages(chatId: String): Future[Seq[ChattrMessage]]
  def updateInbox(userId: String, msg: ChattrMessage): Future[_]
  def loadInbox(userId: String): Future[Seq[ChattrMessage]]
}

class MessageRepositoryImpl(
                             cassandraSession: CassandraSession,
                             actorSystem: ActorSystem[_]
                           ) extends MessageRepository {
  given session: CassandraSession = cassandraSession
  given system: ActorSystem[_] = actorSystem

  private val saveStatementBinder =
    (msg: ChattrMessage, stmt: PreparedStatement) => stmt.bind(
      msg.chatId,
      msg.messageId,
      msg.clientMessageId,
      msg.fromUserId,
      msg.message,
      msg.sentAt.toInstant(ZoneOffset.UTC),
      msg.receivedAt.toInstant(ZoneOffset.UTC),
      msg.deliveredAt.map(_.toInstant(ZoneOffset.UTC)).orNull
    )

  private val saveFlow = Flow[ChattrMessage].via(CassandraFlow.create(CassandraWriteSettings.defaults,
    """
      INSERT INTO chattr.message(
        chat_id,
        message_id,
        client_message_id,
        from_user_id,
        message,
        sent_at,
        received_at,
        delivered_at
      ) VALUES (?,?,?,?,?,?,?,?)""", saveStatementBinder))

  private val updateInboxStatementBinder =
    (inboxMsg: (String, ChattrMessage), stmt: PreparedStatement) => {
      inboxMsg match {
        // s"${message.to}#${message.from}"
        case (userId, message) => stmt.bind(
          userId,
          message.chatId,
          message.fromUserId,
          message.sentAt.toInstant(ZoneOffset.UTC),
          message.messageId,
          message.message
        )
      }
    }

  private val updateInboxFlow = Flow[(String, ChattrMessage)].via(CassandraFlow.create(CassandraWriteSettings.defaults,
    """
      |INSERT INTO chattr.inbox(
        |user_id,
        |chat_id,
        |last_message_from_user_id,
        |last_message_sent_at,
        |last_message_id,
        |last_message
      |) VALUES (?, ?, ?, ?, ?, ?)
      |""".stripMargin, updateInboxStatementBinder))


  def save(msg: ChattrMessage): Future[ChattrMessage] =
    Source.single(msg)
    .via(saveFlow)
    .runWith(Sink.head)

  override def findChatMessages(chatId: String): Future[Seq[ChattrMessage]] =
    CassandraSource("""SELECT * FROM chattr.message WHERE chat_id = ?""", chatId)
      .map(row => ChattrMessage(
        row.getString("chat_id"),
        row.getString("message_id"),
        row.getString("client_message_id"),
        row.getString("from_user_id"),
        row.getString("message"),
        LocalDateTime.ofInstant(row.getInstant("sent_at"), ZoneOffset.UTC),
        LocalDateTime.ofInstant(row.getInstant("received_at"), ZoneOffset.UTC),
        Option(row.getInstant("delivered_at")).map(LocalDateTime.ofInstant(_, ZoneOffset.UTC))
      ))
      .runWith(Sink.seq)

  override def updateInbox(userId: String, msg: ChattrMessage): Future[_] = Source.single((userId, msg))
    .via(updateInboxFlow)
    .runWith(Sink.head)

  override def loadInbox(userId: String): Future[Seq[ChattrMessage]] = CassandraSource(
    """SELECT * FROM chattr.inbox WHERE user_id = ?""", userId)
      .map(row => ChattrMessage(
        row.getString("chat_id"),
        row.getString("last_message_id"),
        "",
        row.getString("last_message_from_user_id"),
        row.getString("last_message"),
        LocalDateTime.ofInstant(row.getInstant("last_message_sent_at"), ZoneOffset.UTC),
        LocalDateTime.ofInstant(row.getInstant("last_message_sent_at"), ZoneOffset.UTC)
      ))
      .runWith(Sink.seq)
}

