package org.chats

import routes.Api

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.chats.config.settings
import org.chats.settings.ServerSettings

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.Failure

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

    println("Starting server")
    val ServerSettings(host, port) = settings.server
    val binding = Http().newServerAt(host, port).bind(Api.routes)

    StdIn.readLine() // let it run until user presses return

    binding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(r => {
        r match {
          case Failure(ex) => system.log.error("Failed to bind to {}:{}!", ex, host, port)
          case scala.util.Success(_) => {}
        }
        system.terminate()
      }) // and shutdown when done
  }
}
