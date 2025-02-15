package org.chats
package routes

import config.mainExceptionHandler

import org.apache.pekko.http.scaladsl.server.{Directives, Route}


object Api extends Directives {
  val routes: Route = Route.seal( // seal the route to invoke mainExceptionHandler
    pathPrefix("api") {
      concat(
        ChatsApi.routes
      )
    }
  )
}

