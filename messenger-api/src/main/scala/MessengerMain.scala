package org.chats

import config.AppConfig
import config.ServerSettings
import service.ClientManagerActor

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.scaladsl.Http

import scala.concurrent.ExecutionContext
import scala.io.StdIn
import scala.util.{Failure, Success}

object MessengerMain {
  def main(args: Array[String]): Unit = {
    println("Starting messenger server")

    val app = AppConfig()
    given system: ActorSystem[ClientManagerActor.Command] = app.system
    given executionContext: ExecutionContext = app.executionContext

    val ServerSettings(host, port) = app.settings.server
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
