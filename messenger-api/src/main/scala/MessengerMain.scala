package org.chats

import service.{ClientManagerActor, Exchange}

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.scaladsl.ClusterSharding
import org.apache.pekko.http.scaladsl.Http

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.{Failure, Success}

// This was ActorSystem[Any] in the Pekko Http docs, not sure if its okay to reuse this system for application's actors
// or we need to build a new one. This somehow works for now.
implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
  Behaviors.setup(context => ClientManagerActor(context)), "my-system", configureClusterPort())

val sharding = ClusterSharding(system)
// Makes sure the ShardRegion is initialized at startup
val shardRegion = Exchange.shardRegion

private def configureClusterPort(): Config = {
  ConfigFactory.parseString(
    s"""
    pekko.remote.artery.canonical.port=${sys.env.getOrElse("CLUSTER_PORT", "7354")}
    """).withFallback(ConfigFactory.load())
}

implicit val executionContext: ExecutionContextExecutor = system.executionContext

object MessengerMain {
  def main(args: Array[String]): Unit = {

    println("Starting messenger server")
    val host = "localhost"
    val port = sys.env.getOrElse("MESSENGER_PORT", "8081").toInt
    val binding = Http().newServerAt(host, port).bind(Api.handleWsRequest)

    StdIn.readLine() // let it run until user presses return

    binding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(r => {
        r match {
          case Failure(ex) => system.log.error("Failed to bind to {}:{}!", ex, host, port)
          case Success(_) => {}
        }
        system.terminate()
      }) // and shutdown when done
  }
}
