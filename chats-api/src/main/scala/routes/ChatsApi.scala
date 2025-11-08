package org.chats
package routes

import config.chatService
import dto.{ApiError, Chat, ChatContent, Chats, Errors, Message, WhisperJsonProtocol}
import routes.Api.{JavaUUID, complete, concat, path, pathEnd, pathPrefix}

import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directives, Route}

import java.time.LocalDateTime

object ChatsApi extends Directives with SprayJsonSupport with WhisperJsonProtocol {
  val routes: Route = pathPrefix("chats") {
    concat(
      (pathEnd & get) {
        complete(
          chatService.getChats.map { c =>
            Chats(c.map(it => Chat(it.chatId, it.lastMessageFromUserId, it.lastMessage)))
         }
        )
      },

      (path(Segment) & get) { chatId =>
        complete(chatService.getChatMessages(chatId).map { messages =>
          ChatContent(messages.map {it => Message(it.messageId, it.fromUserId, it.message, LocalDateTime.now())})
        })
      }
    )
  }
}
