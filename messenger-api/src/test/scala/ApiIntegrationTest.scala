package org.chats

import dto.{InputMessageDto, MessengerJsonProtocol}

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{TextMessage, WebSocketRequest}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import spray.json.*

import scala.concurrent.Future

class ApiIntegrationTest extends AsyncFlatSpec with BeforeAndAfterAll with MessengerJsonProtocol {
  private val config = IntegrationTestConfig()
  private val server = Http()(using config.system).newServerAt("localhost", 0)
  private val binding = server.bind(Api(using config.system, config.system.executionContext).handleWsRequest)

  implicit val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system", ConfigFactory.load("application-client-test.conf"))

  "Clients" should "send and receive messages" in {
    binding.flatMap { b =>
      Thread.sleep(5000)

      val clientSource1 = Source.queue[InputMessageDto](3)
      val clientFlow1 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b.localAddress}/api/connect?userName=foo"))
      val clientSink1 = Sink.queue[String]()

      val client2Source = Source.queue[String](1)
      val clientFlow2 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b.localAddress}/api/connect?userName=bar"))
      val clientSink2 = Sink.queue[String]()

      Thread.sleep(5000)

      val client1Messages = clientSource1
        .map { input => TextMessage(input.toJson.toString) }
        .viaMat(clientFlow1)(Keep.left)
        .map { _.asTextMessage.getStrictText }
        .to(Sink.ignore)
        .run()

      val client2Messages = client2Source.map { input => TextMessage(input) }
        .via(clientFlow2)
        .map { _.asTextMessage.getStrictText }
        .toMat(clientSink2)(Keep.right)
        .run()

      Thread.sleep(5000)

      client1Messages.offer(InputMessageDto("1", "bar", "hi"))
      client1Messages.offer(InputMessageDto("2", "bar", "hey"))
      client1Messages.offer(InputMessageDto("3", "bar", "yo"))

      Thread.sleep(1000)

      Future.sequence((0 to 3).map { i => client2Messages.pull().recover { case _ => None } })
    }.map { it =>
      assert(it.length == 4)
    }
  }

  override protected def afterAll(): Unit = {
    binding.flatMap(_.unbind())
  }
}
