import { config } from '../config.ts'
import type { IncomingMessage, OutgoingMessage } from './dto.ts'

export type ConnectionStatus = 'connecting' | 'open' | 'reconnecting' | 'failed' | 'closed'

type Listener<T> = (value: T) => void

// Same restart policy as the CLI's RestartFlow.withBackoff
const MIN_BACKOFF_MS = 1_000
const MAX_BACKOFF_MS = 5_000
const RANDOM_FACTOR = 0.2
const MAX_RESTARTS = 5
const MAX_RESTARTS_WITHIN_MS = 60_000
// Messages sent while disconnected are buffered, dropping the oldest, like the CLI's actor source
const SEND_BUFFER_SIZE = 256

function connectUrl(userName: string): string {
  return `${config.messengerApiUrl}/connect?userName=${encodeURIComponent(userName)}`
}

// A websocket connection to messenger-api for one user, reconnecting on failure
export class MessengerClient {
  private readonly userName: string
  private socket: WebSocket | null = null
  private reconnectTimer: ReturnType<typeof setTimeout> | undefined
  private restarts: number[] = []
  private sendBuffer: OutgoingMessage[] = []
  private messageListeners = new Set<Listener<IncomingMessage>>()
  private statusListeners = new Set<Listener<ConnectionStatus>>()
  private _status: ConnectionStatus = 'connecting'

  constructor(userName: string) {
    this.userName = userName
    this.connect()
  }

  get status(): ConnectionStatus {
    return this._status
  }

  send(message: OutgoingMessage) {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(message))
    } else if (this._status !== 'closed' && this._status !== 'failed') {
      if (this.sendBuffer.length >= SEND_BUFFER_SIZE) {
        this.sendBuffer.shift()
      }
      this.sendBuffer.push(message)
    }
  }

  // Returns an unsubscribe function
  onMessage(listener: Listener<IncomingMessage>): () => void {
    this.messageListeners.add(listener)
    return () => this.messageListeners.delete(listener)
  }

  // Returns an unsubscribe function
  onStatusChange(listener: Listener<ConnectionStatus>): () => void {
    this.statusListeners.add(listener)
    return () => this.statusListeners.delete(listener)
  }

  close() {
    clearTimeout(this.reconnectTimer)
    this.sendBuffer = []
    this.setStatus('closed')
    this.socket?.close()
    this.socket = null
  }

  private connect() {
    const socket = new WebSocket(connectUrl(this.userName))
    this.socket = socket

    socket.onopen = () => {
      this.setStatus('open')
      const buffered = this.sendBuffer
      this.sendBuffer = []
      buffered.forEach((message) => this.send(message))
    }

    socket.onmessage = (event) => {
      if (typeof event.data !== 'string') {
        return // binary frames are ignored, as in the CLI
      }
      let message: IncomingMessage
      try {
        message = JSON.parse(event.data)
      } catch (e) {
        console.error('Failed to parse message', event.data, e)
        return
      }
      this.messageListeners.forEach((listener) => listener(message))
    }

    // onclose follows onerror, so reconnecting is handled in one place
    socket.onclose = () => {
      if (this.socket === socket && this._status !== 'closed') {
        this.scheduleReconnect()
      }
    }
  }

  private scheduleReconnect() {
    const now = Date.now()
    this.restarts = this.restarts.filter((t) => now - t < MAX_RESTARTS_WITHIN_MS)
    if (this.restarts.length >= MAX_RESTARTS) {
      this.socket = null
      this.sendBuffer = []
      this.setStatus('failed')
      return
    }

    const backoff = Math.min(MAX_BACKOFF_MS, MIN_BACKOFF_MS * 2 ** this.restarts.length)
    const delay = backoff * (1 + Math.random() * RANDOM_FACTOR)
    this.restarts.push(now)
    this.setStatus('reconnecting')
    this.reconnectTimer = setTimeout(() => this.connect(), delay)
  }

  private setStatus(status: ConnectionStatus) {
    if (this._status !== status) {
      this._status = status
      this.statusListeners.forEach((listener) => listener(status))
    }
  }
}
