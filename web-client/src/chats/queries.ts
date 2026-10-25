import { useQuery, type QueryClient } from '@tanstack/react-query'
import { getChats, getMessages } from './chatsApi.ts'
import type { Chat, Message } from './dto.ts'

const chatsKey = (userName: string) => ['chats', userName] as const
const messagesKey = (chatId: string) => ['messages', chatId] as const

// Most recent first
export function useChats(userName: string) {
  return useQuery({
    queryKey: chatsKey(userName),
    queryFn: () => getChats(userName),
    select: ({ chats }) => chats.toSorted((a, b) => b.lastMessageAt - a.lastMessageAt),
  })
}

// Oldest first, for display
export function useMessages(chatId: string) {
  return useQuery({
    queryKey: messagesKey(chatId),
    queryFn: async () => (await getMessages(chatId)).messages.toReversed(),
  })
}

// Both queries are kept current by the websocket (see useLiveUpdates), so a message,
// received or sent, is added to the cache directly instead of refetching
export function addMessageToCache(
  queryClient: QueryClient,
  userName: string,
  chatId: string,
  message: Message,
) {
  queryClient.setQueryData<Message[]>(messagesKey(chatId), (messages) =>
    messages && !messages.some((m) => m.id === message.id) ? [...messages, message] : messages,
  )

  const chat: Chat = {
    id: chatId,
    fromUserId: message.from,
    lastText: message.text,
    lastMessageAt: message.receivedAt,
  }
  queryClient.setQueryData<{ chats: Chat[] }>(chatsKey(userName), (data) =>
    data && { chats: [chat, ...data.chats.filter((c) => c.id !== chatId)] },
  )
}
