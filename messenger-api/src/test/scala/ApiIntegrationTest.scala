package org.chats

import config.AppConfig
import dto.{InputMessageDto, MessengerJsonProtocol}

import com.dimafeng.testcontainers.CassandraContainer
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ws.{TextMessage, WebSocketRequest}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import spray.json.*

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

class ApiIntegrationTest extends AsyncFlatSpec with BeforeAndAfterAll with MessengerJsonProtocol {
  private val containerDef = CassandraContainer.Def(initScript = Some("migrations/messages.cql"))
  private val container = containerDef.createContainer()
  container.start()

  private val config: AppConfig = AppConfig("application-test.conf", Map(
    "datastax-java-driver.basic.contact-points" -> s"[\"127.0.0.1:${container.mappedPort(9042)}\"]",
    "datastax-java-driver.basic.load-balancing-policy.local-datacenter" -> container.cassandraContainer.getLocalDatacenter
  ))

  private val server = Http()(using config.system).newServerAt("localhost", 0)
  private val binding = server.bind(Api(using config.system, config.system.executionContext).handleWsRequest)

  implicit val clientSystem: ActorSystem[_] = ActorSystem(Behaviors.empty, "test-system", ConfigFactory.load("application-client-test.conf"))

  "Clients" should "send and receive messages" in {
    binding
      .flatMap { b =>
        // given
        val clientSource1 = Source.queue[InputMessageDto](3)
        val clientFlow1 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b.localAddress}/api/connect?userName=foo"))
        val clientSink1 = Flow[String].take(1).toMat(Sink.seq[String])(Keep.right)

        val client2Source = Source.queue[String](1)
        val clientFlow2 = Http().webSocketClientFlow(WebSocketRequest(s"ws:/${b.localAddress}/api/connect?userName=bar"))
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
        for {f1 <- client1Out; f2 <- client2Out} yield (f1, f2)
      }
      .map { case (client1Out, client2Out) =>
        assert(client1Out == Seq(
          """{"from":"","id":"","text":"You joined the chat"}"""
        ))

        assert(client2Out.toSet == Set(
          """{"from":"","id":"","text":"You joined the chat"}""",
          """{"from":"foo","id":"1","text":"hi"}""",
          """{"from":"foo","id":"2","text":"hey"}""",
          """{"from":"foo","id":"3","text":"yo"}"""
        ))
      }
  }

  override protected def afterAll(): Unit = {
    clientSystem.terminate()
    Await.ready(clientSystem.whenTerminated.flatMap(_ => {
      config.system.terminate()
      config.system.whenTerminated
    })(ExecutionContext.global), 5.seconds)
  }
}
