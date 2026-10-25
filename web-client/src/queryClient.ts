import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    // Live updates come over the websocket, see chats/useLiveUpdates.ts
    queries: { staleTime: Infinity, refetchOnWindowFocus: false },
  },
})
