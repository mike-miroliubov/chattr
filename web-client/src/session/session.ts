import { useSyncExternalStore } from 'react'
import { getSnapshot, subscribe, type SessionState } from './sessionStore.ts'

export { login, logout } from './sessionStore.ts'

export function useSession(): SessionState {
  return useSyncExternalStore(subscribe, getSnapshot)
}
