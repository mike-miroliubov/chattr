// Wire format of messenger-api's websocket (see messenger-api/src/main/scala/dto/MessengerDto.scala),
// named from the client's point of view

// A message this client sends
export interface OutgoingMessage {
  id: string
  to: string
  text: string
}

// A message this client receives
export interface IncomingMessage {
  id: string
  from: string
  text: string
  chatId: string
}
