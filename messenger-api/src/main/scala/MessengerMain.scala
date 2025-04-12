package org.chats

import config.AppConfig
import service.ClientManagerActor

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http

import scala.concurrent.ExecutionContext
import scala.io.StdIn
import scala.util.{Failure, Success}

private def customizeConfigWithEnvironment(): Config = {
  if (System.getenv("TEST") == "true") {
    ConfigFactory.load("application-test.conf")
  } else {
    ConfigFactory.parseString(
      s"""
      pekko.remote.artery.canonical.port=${sys.env.getOrElse("CLUSTER_PORT", "7354")}
      pekko.persistence.journal.proxy.start-target-journal = ${if sys.env.isDefinedAt("IS_MAIN_INSTANCE") then "on" else "off"}
      pekko.persistence.snapshot-store.proxy.start-target-snapshot-store = ${if sys.env.isDefinedAt("IS_MAIN_INSTANCE") then "on" else "off"}
      """).withFallback(ConfigFactory.load())
  }

}

object MessengerMain {
  def main(args: Array[String]): Unit = {
    println("Starting messenger server")

    val app = AppConfig()
    given system: ActorSystem[ClientManagerActor.Command] = app.system
    given executionContext: ExecutionContext = app.executionContext

    val host = "localhost"
    val port = sys.env.getOrElse("MESSENGER_PORT", "8081").toInt
    val binding = Http().newServerAt(host, port).bind(Api().handleWsRequest)

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
