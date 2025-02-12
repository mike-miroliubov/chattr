package org.chats
package routes

import dto.{ChatContent, Chats, WhisperJsonProtocol}

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import org.chats.config.chatService
import org.chats.routes.Api.{JavaUUID, complete, concat, path, pathEnd, pathPrefix}

object ChatsApi extends Directives with SprayJsonSupport with WhisperJsonProtocol {
  val routes: Route = pathPrefix("chats") {
    concat(
      (pathEnd & get) {
        complete(Chats(chatService.getChats))
      },
      (path(JavaUUID) & get) { uuid =>
        complete(ChatContent(chatService.getChatMessages(uuid.toString)))
      }
    )
  }
}
