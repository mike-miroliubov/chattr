import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import type { MessengerClient } from '../messenger/MessengerClient.ts'
import { addMessageToCache } from './queries.ts'

// Feeds messages from the websocket into the query cache, and refetches everything after
// a reconnect, since messages sent meanwhile were never delivered to this client
export function useLiveUpdates(userName: string, messenger: MessengerClient | null) {
  const queryClient = useQueryClient()
  const wasDisconnected = useRef(false)

  useEffect(() => {
    if (!messenger) {
      return
    }
    const unsubscribeMessages = messenger.onMessage((message) => {
      // Service messages, like the greeting on connect, don't belong to any chat
      if (!message.chatId) {
        return
      }
      addMessageToCache(queryClient, userName, message.chatId, {
        id: message.id,
        from: message.from,
        text: message.text,
        receivedAt: Date.now(),
      })
    })
    const unsubscribeStatus = messenger.onStatusChange((status) => {
      if (status === 'reconnecting') {
        wasDisconnected.current = true
      } else if (status === 'open' && wasDisconnected.current) {
        wasDisconnected.current = false
        void queryClient.invalidateQueries()
      }
    })
    return () => {
      unsubscribeMessages()
      unsubscribeStatus()
    }
  }, [messenger, queryClient, userName])
}
