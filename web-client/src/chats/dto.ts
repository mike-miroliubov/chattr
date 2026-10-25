// Wire format of chats-api (see chats-api/src/main/scala/dto/ChatsDto.scala).
// Timestamps are epoch milliseconds.

export interface Chat {
  id: string
  // Author of the last message
  fromUserId: string
  lastText: string
  lastMessageAt: number
}

export interface Chats {
  chats: Chat[]
}

export interface Message {
  id: string
  from: string
  text: string
  receivedAt: number
}

export interface ChatContent {
  messages: Message[]
}
