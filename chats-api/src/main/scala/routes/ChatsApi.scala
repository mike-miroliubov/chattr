package org.chats
package routes

import config.chatController
import dto.*
import routes.Api.{complete, concat, path, pathEnd, pathPrefix}

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.server.{Directives, Route}

object ChatsApi extends Directives with SprayJsonSupport with ChattrJsonProtocol {
  val routes: Route = pathPrefix("chats") {
    concat(
      (pathEnd & get & parameter("user_id")) { userId =>
          complete(chatController.getChats(userId))
      },
      (path(Segment) & get) { chatId =>
        complete(chatController.getChatMessages(chatId))
      }
    )
  }
}
