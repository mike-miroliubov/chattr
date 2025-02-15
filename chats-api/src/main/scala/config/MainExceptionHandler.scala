package org.chats
package config

import org.apache.pekko.http.scaladsl.model.StatusCodes.InternalServerError
import org.apache.pekko.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, MediaTypes}
import org.apache.pekko.http.scaladsl.server.Directives.{complete, extractLog}
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import spray.json.*
import spray.json.DefaultJsonProtocol.{StringJsonFormat, mapFormat}

private val ERROR_JSON = Map("error" -> "Internal error when processing request").toJson.toString

/**
 * A custom error handler that returns a JSON error payload instead of a plain text message
 */
implicit def mainExceptionHandler: ExceptionHandler = ExceptionHandler {
  case e: scala.Exception =>
    extractLog { log =>
      log.error(e, "Error during request processing")
      complete(HttpResponse(
        status = InternalServerError,
        entity = HttpEntity(
          ContentType(MediaTypes.`application/json`),
          ERROR_JSON
        )
      ))
    }
}
