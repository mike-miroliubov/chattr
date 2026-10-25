import { MessengerClient, type ConnectionStatus } from '../messenger/MessengerClient.ts'
import { queryClient } from '../queryClient.ts'

// Mirrors the CLI: logging in is just picking a user name (the APIs take it as the user id)
// and opening the messenger websocket for it
export interface SessionState {
  userName: string | null
  // Present while logged in
  messenger: MessengerClient | null
  connectionStatus: ConnectionStatus
}

const STORAGE_KEY = 'chattr.userName'

// Storage can be unavailable (e.g. blocked site data); the session then just won't survive a reload
function readStoredUserName(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function storeUserName(userName: string | null) {
  try {
    if (userName) {
      localStorage.setItem(STORAGE_KEY, userName)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // ignore, see readStoredUserName
  }
}

const loggedOut: SessionState = { userName: null, messenger: null, connectionStatus: 'closed' }

let state = loggedOut
let unsubscribeStatus = () => {}
const listeners = new Set<() => void>()

function setState(next: SessionState) {
  state = next
  listeners.forEach((listener) => listener())
}

function start(userName: string) {
  const messenger = new MessengerClient(userName)
  unsubscribeStatus = messenger.onStatusChange((connectionStatus) => {
    if (state.messenger === messenger) {
      setState({ ...state, connectionStatus })
    }
  })
  setState({ userName, messenger, connectionStatus: messenger.status })
}

export function login(userName: string) {
  logout()
  storeUserName(userName)
  start(userName)
}

export function logout() {
  unsubscribeStatus()
  state.messenger?.close()
  storeUserName(null)
  // Don't show the next user anything cached for this one
  queryClient.clear()
  setState(loggedOut)
}

// Resumes the session remembered from a previous visit, if any
export function restoreSession() {
  const userName = readStoredUserName()
  if (userName && !state.userName) {
    start(userName)
  }
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function getSnapshot(): SessionState {
  return state
}

// Don't leave the old socket open when this module is hot-reloaded in dev
import.meta.hot?.dispose(() => {
  unsubscribeStatus()
  state.messenger?.close()
})
