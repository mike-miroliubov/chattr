package org.chats
package routes

import config.chatService
import dto.{ChatContent, Chats, Errors, WhisperJsonProtocol}
import routes.Api.{JavaUUID, complete, concat, path, pathEnd, pathPrefix}

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directives, Route}

object ChatsApi extends Directives with SprayJsonSupport with WhisperJsonProtocol {
  val routes: Route = pathPrefix("chats") {
    concat(
      (pathEnd & get) {
        complete(Chats(chatService.getChats))
      },

      (path(JavaUUID) & get) { uuid =>
        chatService.getChatMessages(uuid.toString) match {
          case Some(messages) => complete(ChatContent(messages))
          case None => complete(StatusCodes.BadRequest, Errors.ObjectNotFoundError)
        }
      }
    )
  }
}
