import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type ProxyOptions } from 'vite'

// Dev proxy mirroring the local nginx (see local-dev/backend-simple): the prefix is stripped
// and the rest is passed to the service. Targets default to that nginx; point them straight at
// the services with CHATTR_DEV_CHATS_TARGET / CHATTR_DEV_MESSENGER_TARGET (e.g. in .env.local).
function serviceProxy(prefix: string, target: string): Record<string, ProxyOptions> {
  return {
    [prefix]: {
      target,
      changeOrigin: true,
      ws: true,
      rewrite: (path) => path.slice(prefix.length),
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'CHATTR_')

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        ...serviceProxy('/chats', env.CHATTR_DEV_CHATS_TARGET ?? 'http://localhost/chats'),
        ...serviceProxy('/messenger', env.CHATTR_DEV_MESSENGER_TARGET ?? 'http://localhost/messenger'),
      },
    },
  }
})
