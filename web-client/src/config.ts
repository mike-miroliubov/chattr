// Server endpoints, the same two settings as the CLI's chattr.server config.
// Set via VITE_* env vars (e.g. in .env.local); defaults are paths on the app's own origin,
// served by the local nginx in deployment and by the Vite proxy in dev.
export const config = {
  chatsApiUrl: import.meta.env.VITE_CHATS_API_URL ?? '/chats/api',
  messengerApiUrl: toWebSocketUrl(import.meta.env.VITE_MESSENGER_API_URL ?? '/messenger/api'),
}

// Accepts ws(s)://, http(s):// or a path relative to the app's origin
function toWebSocketUrl(url: string): string {
  const resolved = new URL(url, location.href)
  resolved.protocol = resolved.protocol.replace(/^http/, 'ws')
  return resolved.toString().replace(/\/$/, '')
}
