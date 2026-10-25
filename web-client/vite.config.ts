import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Backend runs behind the local nginx (see local-dev/backend-simple), same as the CLI client uses
const backendUrl = process.env.CHATTR_BACKEND_URL ?? 'http://localhost'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/chats': { target: backendUrl, changeOrigin: true },
      '/messenger': { target: backendUrl, changeOrigin: true, ws: true },
    },
  },
})
