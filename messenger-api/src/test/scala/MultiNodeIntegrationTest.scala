package org.chats

import config.{ExchangeShardRegion, GroupShardRegion, ShardingConfig}
import dto.{InputMessageDto, MessengerJsonProtocol}
import service.{ClientActor, ClientManagerActor}
import service.GroupExchange.{AddMember, MakeGroup}

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.chats.repository.MessageRepository
import org.scalatest.{BeforeAndAfterAll, Tag}
import org.scalatest.flatspec.AsyncFlatSpec
import spray.json.*

import java.net.ServerSocket
import java.util.UUID
import scala.collection.immutable.Seq
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future, Promise}
import scala.util.Using

object MultiNode extends Tag("org,chats.tags.MultiNode")

class MultiNodeIntegrationTest extends AsyncFlatSpec with BeforeAndAfterAll with MessengerJsonProtocol {
  private val seedNodePort = findFreePort()
  private val config1 = NodeConfig(seedNodePort)
  private val server = Http()(using config1.system).newServerAt("localhost", 0)
  private val binding1 = server.bind(Api(using config1.system, config1.system.executionContext).handleWsRequest)

  private val clusterPort2 = findFreePort()
  private val config2 = NodeConfig(clusterPort2)
  private val server2 = Http()(using config2.system).newServerAt("localhost", 0)
  private val binding2 = server.bind(Api(using config2.system, config2.system.executionContext).handleWsRequest)

  implicit val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system", ConfigFactory.load("application-client-test.conf"))

  behavior of "Clients on different nodes"

  they should "connect and receive messages" taggedAs MultiNode in {
    Future.sequence(Seq(binding1, binding2)).flatMap { case Seq(b1, b2) =>
      Thread.sleep(1000)

      val clientSource1 = Source.queue[InputMessageDto](3)
      val clientFlow1 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b1.localAddress}/api/connect?userName=foo"))
      val clientSink1 = Flow[String].take(1).toMat(Sink.seq[String])(Keep.right)

      val client2Source = Source.queue[String](1)
      val clientFlow2 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b2.localAddress}/api/connect?userName=bar"))
      val clientSink2 = Flow[String].take(4).toMat(Sink.seq[String])(Keep.right)

      // Client 1 connects
      val (client1In, client1Out) = clientSource1
        .map { input => TextMessage(input.toJson.toString) }
        .viaMat(clientFlow1)(Keep.left)
        .map { _.asTextMessage.getStrictText }
        .toMat(clientSink1)(Keep.both)
        .run()

      // Client 2 connects
      val (client2In, client2Out) = client2Source
        .map { input => TextMessage(input) }
        .viaMat(clientFlow2)(Keep.left)
        .map { _.asTextMessage.getStrictText }
        .toMat(clientSink2)(Keep.both)
        .run()

      // when
      // Client 1 sends 3 messages
      client1In.offer(InputMessageDto("1", "bar", "hi"))
      client1In.offer(InputMessageDto("2", "bar", "hey"))
      client1In.offer(InputMessageDto("3", "bar", "yo"))

      // then
      Future.sequence(Seq(client1Out, client2Out)).map {
        case Seq(c1, c2) => (c1, c2, client1In, client2In)
      }
    }
    .map { case (c1, c2, client1In, client2In) =>
      assert(c1 == Seq(
        """{"from":"","id":"","text":"You joined the chat"}"""
      ))

      assert(c2.toSet == Set(
        """{"from":"","id":"","text":"You joined the chat"}""",
        """{"from":"foo","id":"1","text":"hi"}""",
        """{"from":"foo","id":"2","text":"hey"}""",
        """{"from":"foo","id":"3","text":"yo"}"""
      ))
      client1In.complete()
      client2In.complete()

      succeed
    }
  }

  they should "create group and message in a group" taggedAs MultiNode in {
    val groupName = UUID.randomUUID().toString

    Future.sequence(Seq(binding1, binding2)).flatMap { case Seq(b1, b2) =>
      Thread.sleep(1000)
      // given
      val receiveMessage = Flow[Message].map(m => m.asTextMessage.getStrictText)

      val clientSource1 = Source.queue[InputMessageDto](3)
      val clientFlow1 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b1.localAddress}/api/connect?userName=user1"))
      val clientSink1 = receiveMessage.take(7).toMat(Sink.seq[String])(Keep.right)

      val clientSource2 = Source.queue[InputMessageDto](3)
      val clientFlow2 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b2.localAddress}/api/connect?userName=user2"))
      val clientSink2 = receiveMessage.take(7).toMat(Sink.seq[String])(Keep.right)

      val clientSource3 = Source.queue[InputMessageDto](3)
      val clientFlow3 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b2.localAddress}/api/connect?userName=user3"))
      val clientSink3 = receiveMessage.take(7).toMat(Sink.seq[String])(Keep.right)

      config1.groupSharding ! ShardingEnvelope(s"g#$groupName", MakeGroup("user1", Set()))
      config1.groupSharding ! ShardingEnvelope(s"g#$groupName", AddMember("user2"))
      config1.groupSharding ! ShardingEnvelope(s"g#$groupName", AddMember("user3"))

      def publishMessage(i: InputMessageDto) = TextMessage(i.toJson.toString)

      // connect 3 clients
      val ((client1In, client1Connected), client1Out) = clientSource1
        .map(publishMessage)
        .viaMat(clientFlow1)(Keep.both)
        .toMat(clientSink1)(Keep.both).run()

      val ((client2In, client2Connected), client2Out) = clientSource2
        .map(publishMessage)
        .viaMat(clientFlow2)(Keep.both)
        .toMat(clientSink2)(Keep.both).run()

      val ((client3In, client3Connected), client3Out) = clientSource3
        .map(publishMessage)
        .viaMat(clientFlow3)(Keep.both)
        .toMat(clientSink3)(Keep.both).run()

      Future.sequence(Seq(client1Connected, client2Connected, client3Connected))
      .map { _ =>
        (client1In, client2In, client3In, client1Out, client2Out, client3Out)
      }
    }
    // wait until all clients connect
    .flatMap { (client1In, client2In, client3In, client1Out, client2Out, client3Out) =>
      // when
      for {
        (client, clientId) <- Seq(client1In, client2In, client3In).zipWithIndex
        (message, msgId) <- Seq("hey!", "ho!", "lets go!").zipWithIndex
      } yield {
        client.offer(InputMessageDto(s"${clientId + 1}-${msgId + 1}", s"g#$groupName", message))
      }

      // then

      for {
        c1 <- client1Out
        c2 <- client2Out
        c3 <- client3Out
      } yield {
        assert(c1.toSet == Set( // messages can come out of order
          """{"from":"","id":"","text":"You joined the chat"}""",
          """{"from":"user2","id":"2-1","text":"hey!"}""",
          """{"from":"user2","id":"2-2","text":"ho!"}""",
          """{"from":"user2","id":"2-3","text":"lets go!"}""",
          """{"from":"user3","id":"3-1","text":"hey!"}""",
          """{"from":"user3","id":"3-2","text":"ho!"}""",
          """{"from":"user3","id":"3-3","text":"lets go!"}"""
        ))

        assert(c2.toSet == Set(
          """{"from":"","id":"","text":"You joined the chat"}""",
          """{"from":"user1","id":"1-1","text":"hey!"}""",
          """{"from":"user1","id":"1-2","text":"ho!"}""",
          """{"from":"user1","id":"1-3","text":"lets go!"}""",
          """{"from":"user3","id":"3-1","text":"hey!"}""",
          """{"from":"user3","id":"3-2","text":"ho!"}""",
          """{"from":"user3","id":"3-3","text":"lets go!"}"""
        ))

        assert(c3.toSet == Set(
          """{"from":"","id":"","text":"You joined the chat"}""",
          """{"from":"user1","id":"1-1","text":"hey!"}""",
          """{"from":"user1","id":"1-2","text":"ho!"}""",
          """{"from":"user1","id":"1-3","text":"lets go!"}""",
          """{"from":"user2","id":"2-1","text":"hey!"}""",
          """{"from":"user2","id":"2-2","text":"ho!"}""",
          """{"from":"user2","id":"2-3","text":"lets go!"}"""
        ))

        client1In.complete()
        client2In.complete()
        client3In.complete()

        succeed
      }
    }
  }

  override protected def afterAll(): Unit = {
    config2.system.log.info("Teardown started")
    config2.system.terminate() // first terminate the secondary node
    Await.ready(config2.system.whenTerminated.flatMap(r => {
      config1.system.log.info("Secondary node downed")
      config1.system.terminate()
      config1.system.whenTerminated
    })(ExecutionContext.global), 10.seconds)
  }

  class NodeConfig(clusterPort: Int) {
    // This was ActorSystem[Any] in the Pekko Http docs, not sure if its okay to reuse this system for application's actors
    // or we need to build a new one. This somehow works for now.
    implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
      Behaviors.setup(context => ClientManagerActor(context, userSharding, groupSharding, messageRepository)),
      s"my-system",
      ConfigFactory.parseString(
        s"""
        pekko.remote.artery.canonical.port=$clusterPort
        pekko.cluster.seed-nodes=["pekko://my-system@127.0.0.1:$seedNodePort"]
        pekko.cluster.jmx.multi-mbeans-in-same-jvm = on
        """).withFallback(ConfigFactory.load("application-test.conf"))
    )
    val executionContext: ExecutionContextExecutor = system.executionContext
    val messageRepository: MessageRepository = new MessageRepository {
      // a no-op repository
      override def save(msg: ClientActor.IncomingMessage): Future[ClientActor.IncomingMessage] = Promise.successful(msg).future
      override def findChatMessages(chatId: String): Future[Seq[ClientActor.IncomingMessage]] = Promise.successful(Seq()).future
    }

    // Makes sure the ShardRegion is initialized at startup
    val ShardingConfig(userSharding: ExchangeShardRegion, groupSharding: GroupShardRegion) = ShardingConfig()

    // TODO: remove, this is a test group
    groupSharding ! ShardingEnvelope("g#test-group", MakeGroup("morpheus", Set("kite", "foo", "neo")))
  }

  private def findFreePort() = Using(new ServerSocket(0)) { socket => socket.getLocalPort }.get
}
