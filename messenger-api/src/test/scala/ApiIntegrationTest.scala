package org.chats

import dto.{InputMessageDto, MessengerJsonProtocol}
import service.{Exchange, GroupExchange}

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{TextMessage, WebSocketRequest}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import spray.json.*

import scala.concurrent.Future

class ApiIntegrationTest extends AsyncFlatSpec with BeforeAndAfterAll with MessengerJsonProtocol {
//  implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
//    Behaviors.setup(context => ClientManagerActor(context)), "my-system", customizeConfigWithEnvironment())

//  val sharding = ClusterSharding(system)
//  // Makes sure the ShardRegion is initialized at startup
//  val shardRegion = Exchange.shardRegion
//  val groupShardRegion = GroupExchange.shardRegion

  private val server = Http().newServerAt("localhost", 0)
  private val binding = server.bind(Api.handleWsRequest)

  "Clients" should "send and receive messages" in {
    binding.flatMap { b =>
      Thread.sleep(15000)

//      val clientSource1 = Source(Seq(
//        InputMessageDto("1", "bar", "hi"),
//        InputMessageDto("2", "bar", "hey"),
//        InputMessageDto("3", "bar", "yo")
//      ))
      val (clientQueue1, clientSource1) = Source.queue[InputMessageDto](3).preMaterialize()
      val clientFlow1 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b.localAddress}/api/connect?userName=foo"))
      val clientSink1 = Sink.queue[String]()

      val client2Source = Source.queue[String](1)
      val clientFlow2 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b.localAddress}/api/connect?userName=bar"))
      val clientSink2 = Sink.queue[String]()

      val client1Messages = clientSource1
        .map { input => TextMessage(input.toJson.toString) }
        .via(clientFlow1)
        .map { _.asTextMessage.getStrictText }
        .toMat(clientSink1)(Keep.right)
        .run()

      val client2Messages = client2Source.map { input => TextMessage(input) }
        .via(clientFlow2)
        .map { _.asTextMessage.getStrictText }
        .toMat(clientSink2)(Keep.right)
        .run()

      Thread.sleep(5000)

      clientQueue1.offer(InputMessageDto("1", "bar", "hi"))
      clientQueue1.offer(InputMessageDto("2", "bar", "hey"))
      clientQueue1.offer(InputMessageDto("3", "bar", "yo"))

      Thread.sleep(1000)

      Future.sequence((0 to 3).map { i => client2Messages.pull().recover { case _ => None } })
    }.map { it =>
      assert(it.length == 3)
    }
  }

  override protected def afterAll(): Unit = {
    binding.flatMap(_.unbind())
  }
}
