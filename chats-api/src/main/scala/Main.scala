package org.chats

import routes.Api

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.chats.config.settings
import org.chats.settings.ServerSettings

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.io.StdIn
import scala.util.Failure

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

    system.log.info("Starting server: {}", settings)
    val ServerSettings(host, port) = settings.server
    val binding = Http().newServerAt(host, port).bind(Api.routes)

    sys.addShutdownHook {
      system.log.info("Shutting down")
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

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}
