package org.chats

import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}

object Api extends Directives {
  val routes = pathPrefix("api") {
    path("connect") {
      handleWebSocketMessages(wsConnect)
    }
  }

  def wsConnect: Flow[Message, Message, Any] = Flow[Message].mapConcat {
    case m: TextMessage => List(TextMessage(Source.single("You said: ") ++ m.textStream))
    case m: BinaryMessage => {
      m.dataStream.runWith(Sink.ignore)
      Nil
    }
  }
}