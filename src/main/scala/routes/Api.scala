package org.chats
package routes

import org.apache.pekko.http.scaladsl.server.{Directives, Route}

object Api extends Directives {
  val routes: Route = pathPrefix("api") {
    concat(
      ChatsApi.routes
    )
  }
}

