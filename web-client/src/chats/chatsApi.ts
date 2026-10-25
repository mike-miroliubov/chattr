import { config } from '../config.ts'
import type { ChatContent, Chats } from './dto.ts'

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${config.chatsApiUrl}${path}`)
  if (!response.ok) {
    throw new Error(`chats-api responded with ${response.status} ${response.statusText}`)
  }
  return response.json()
}

export function getChats(userName: string): Promise<Chats> {
  return getJson(`/chats?user_id=${encodeURIComponent(userName)}`)
}

// The latest page of messages, newest first
export function getMessages(chatId: string): Promise<ChatContent> {
  return getJson(`/chats/${encodeURIComponent(chatId)}`)
}
