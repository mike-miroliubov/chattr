package org.chats

import config.{ExchangeShardRegion, GroupShardRegion, ShardingConfig}
import dto.{InputMessageDto, MessengerJsonProtocol}
import service.ClientManagerActor
import service.GroupExchange.{AddMember, MakeGroup}

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import spray.json.*

import java.net.ServerSocket
import java.util.UUID
import scala.collection.immutable.Seq
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.Using

class MultiNodeIntegrationTest extends AnyFlatSpec with BeforeAndAfterAll with MessengerJsonProtocol {
  private val seedNodePort = findFreePort()
  private val config1 = NodeConfig(seedNodePort)
  private val server = Http()(using config1.system).newServerAt("localhost", 0)
  private val binding1 = Await.result(
    server.bind(Api(using config1.system, config1.system.executionContext).handleWsRequest), 5.seconds)

  Thread.sleep(1000)

  private val clusterPort2 = findFreePort()
  private val config2 = NodeConfig(clusterPort2)
  private val server2 = Http()(using config2.system).newServerAt("localhost", 0)
  private val binding2 = Await.result(
    server.bind(Api(using config2.system, config2.system.executionContext).handleWsRequest), 5.seconds)

  Thread.sleep(1000)

  implicit val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system", ConfigFactory.load("application-client-test.conf"))

  "Clients" should "connect to different nodes" in {
    val clientSource1 = Source.queue[InputMessageDto](3)
    val clientFlow1 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${binding1.localAddress}/api/connect?userName=foo"))
    val clientSink1 = Flow[String].take(1).toMat(Sink.seq[String])(Keep.right)

    val client2Source = Source.queue[String](1)
    val clientFlow2 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${binding2.localAddress}/api/connect?userName=bar"))
    val clientSink2 = Flow[String].take(4).toMat(Sink.seq[String])(Keep.right)

    // Client 1 connects
    val (client1In, client1Out) = clientSource1
      .map { input => TextMessage(input.toJson.toString) }
      .viaMat(clientFlow1)(Keep.left)
      .map { _.asTextMessage.getStrictText }
      .toMat(clientSink1)(Keep.both)
      .run()

    // Client 2 connects
    val client2Out = client2Source.map { input => TextMessage(input) }
      .via(clientFlow2)
      .map { _.asTextMessage.getStrictText }
      .toMat(clientSink2)(Keep.right)
      .run()

    // when
    // Client 1 sends 3 messages
    client1In.offer(InputMessageDto("1", "bar", "hi"))
    client1In.offer(InputMessageDto("2", "bar", "hey"))
    client1In.offer(InputMessageDto("3", "bar", "yo"))

    // then
    assert(Await.result(client1Out, 5.seconds) == Seq(
      """{"from":"","id":"","text":"You joined the chat"}"""
    ))

    assert(Await.result(client2Out, 5.seconds) == Seq(
      """{"from":"","id":"","text":"You joined the chat"}""",
      """{"from":"foo","id":"1","text":"hi"}""",
      """{"from":"foo","id":"2","text":"hey"}""",
      """{"from":"foo","id":"3","text":"yo"}"""
    ))
  }

  "Clients" should "create group and message in a group" in {
    // given
    val groupName = UUID.randomUUID().toString
    val receiveMessage = Flow[Message].map(m => m.asTextMessage.getStrictText)

    val clientSource1 = Source.queue[InputMessageDto](3)
    val clientFlow1 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${binding1.localAddress}/api/connect?userName=user1"))
    val clientSink1 = receiveMessage.take(7).toMat(Sink.seq[String])(Keep.right)

    val clientSource2 = Source.queue[InputMessageDto](3)
    val clientFlow2 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${binding2.localAddress}/api/connect?userName=user2"))
    val clientSink2 = receiveMessage.take(7).toMat(Sink.seq[String])(Keep.right)

    val clientSource3 = Source.queue[InputMessageDto](3)
    val clientFlow3 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${binding2.localAddress}/api/connect?userName=user3"))
    val clientSink3 = receiveMessage.take(7).toMat(Sink.seq[String])(Keep.right)

    config1.groupSharding ! ShardingEnvelope(s"g#$groupName", MakeGroup("user1", Set()))
    config1.groupSharding ! ShardingEnvelope(s"g#$groupName", AddMember("user2"))
    config1.groupSharding ! ShardingEnvelope(s"g#$groupName", AddMember("user3"))

    def publishMessage(i: InputMessageDto) = TextMessage(i.toJson.toString)

    // connect 3 clients
    val (client1In, client1Out) = clientSource1
      .map(publishMessage)
      .viaMat(clientFlow1)(Keep.left)
      .toMat(clientSink1)(Keep.both).run()

    val (client2In, client2Out) = clientSource2
      .map(publishMessage)
      .viaMat(clientFlow2)(Keep.left)
      .toMat(clientSink2)(Keep.both).run()

    val (client3In, client3Out) = clientSource3
      .map(publishMessage)
      .viaMat(clientFlow3)(Keep.left)
      .toMat(clientSink3)(Keep.both).run()

    // when
    for {
      (client, clientId) <- Seq(client1In, client2In, client3In).zipWithIndex
      (message, msgId) <- Seq("hey!", "ho!", "lets go!").zipWithIndex
    } yield {
      client.offer(InputMessageDto(s"${clientId+1}-${msgId+1}", s"g#$groupName", message))
    }

    // then
    assert(Await.result(client1Out, 5.seconds).toSet == Set( // messages can come out of order
      """{"from":"","id":"","text":"You joined the chat"}""",
      """{"from":"user2","id":"2-1","text":"hey!"}""",
      """{"from":"user2","id":"2-2","text":"ho!"}""",
      """{"from":"user2","id":"2-3","text":"lets go!"}""",
      """{"from":"user3","id":"3-1","text":"hey!"}""",
      """{"from":"user3","id":"3-2","text":"ho!"}""",
      """{"from":"user3","id":"3-3","text":"lets go!"}"""
    ))

    assert(Await.result(client2Out, 5.seconds).toSet == Set(
      """{"from":"","id":"","text":"You joined the chat"}""",
      """{"from":"user1","id":"1-1","text":"hey!"}""",
      """{"from":"user1","id":"1-2","text":"ho!"}""",
      """{"from":"user1","id":"1-3","text":"lets go!"}""",
      """{"from":"user3","id":"3-1","text":"hey!"}""",
      """{"from":"user3","id":"3-2","text":"ho!"}""",
      """{"from":"user3","id":"3-3","text":"lets go!"}"""
    ))

    assert(Await.result(client3Out, 5.seconds).toSet == Set(
      """{"from":"","id":"","text":"You joined the chat"}""",
      """{"from":"user1","id":"1-1","text":"hey!"}""",
      """{"from":"user1","id":"1-2","text":"ho!"}""",
      """{"from":"user1","id":"1-3","text":"lets go!"}""",
      """{"from":"user2","id":"2-1","text":"hey!"}""",
      """{"from":"user2","id":"2-2","text":"ho!"}""",
      """{"from":"user2","id":"2-3","text":"lets go!"}"""
    ))
  }

  override protected def afterAll(): Unit = {
    binding1.unbind()
    binding2.unbind()
  }

  class NodeConfig(clusterPort: Int) {
    // This was ActorSystem[Any] in the Pekko Http docs, not sure if its okay to reuse this system for application's actors
    // or we need to build a new one. This somehow works for now.
    implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
      Behaviors.setup(context => ClientManagerActor(context, userSharding, groupSharding)),
      s"my-system",
      ConfigFactory.parseString(
        s"""
        pekko.remote.artery.canonical.port=$clusterPort
        pekko.cluster.seed-nodes=["pekko://my-system@127.0.0.1:$seedNodePort"]
        """).withFallback(ConfigFactory.load("application-test.conf"))
    )
    val executionContext: ExecutionContextExecutor = system.executionContext

    // Makes sure the ShardRegion is initialized at startup
    val ShardingConfig(userSharding: ExchangeShardRegion, groupSharding: GroupShardRegion) = ShardingConfig()

    // TODO: remove, this is a test group
    groupSharding ! ShardingEnvelope("g#test-group", MakeGroup("morpheus", Set("kite", "foo", "neo")))
  }

  private def findFreePort() = Using(new ServerSocket(0)) { socket => socket.getLocalPort }.get
}
