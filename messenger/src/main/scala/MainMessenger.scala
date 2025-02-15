package org.chats

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.chats.service.ClientManagerActor

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.Failure

// This was ActorSystem[Any] in the Pekko Http docs, not sure if its okay to reuse this system for application's actors
// or we need to build a new one. This somehow works for now.
implicit val system: ActorSystem[ClientManagerActor.Command] = ActorSystem(
  Behaviors.setup(context => ClientManagerActor(context)), "my-system")

implicit val executionContext: ExecutionContextExecutor = system.executionContext

object MainMessenger {
  def main(args: Array[String]): Unit = {

    println("Starting messenger server")
    val host = "localhost"
    val port = 8081
    val binding = Http().newServerAt(host, port).bindSync(Api.handleWsRequest)

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
