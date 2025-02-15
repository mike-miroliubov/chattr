package org.chats

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn
import scala.util.Failure

implicit val system: ActorSystem[Any] = ActorSystem(Behaviors.empty, "my-system")

object MainMessenger {
  def main(args: Array[String]): Unit = {
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

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
