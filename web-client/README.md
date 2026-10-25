# web-client

Browser SPA for Chattr (React + TypeScript + Vite). Feature-parity target: `cli-client`.

## Development
Requires Node 20.19+ (or 22.12+).
```
npm install
npm run dev      # http://localhost:5173
```
## Configuration
Server endpoints are the same two settings as the CLI's `chattr.server`, set as env vars
(put local overrides in `web-client/.env.local`, which is not committed):

| Variable | Default | Example without nginx |
|---|---|---|
| `VITE_CHATS_API_URL` | `/chats/api` | `http://localhost:8080/api` |
| `VITE_MESSENGER_API_URL` | `/messenger/api` | `ws://localhost:8081/api` |

The defaults are paths on the app's own origin. In dev, the Vite server proxies `/chats/*` and
`/messenger/*` (incl. WebSocket) to the backend nginx at `http://localhost` (override with
`CHATTR_BACKEND_URL`), see `local-dev/backend-simple`. Restart `npm run dev` after changing env files.

## Other scripts
```
npm run build    # type-check + production build into dist/
npm run lint     # oxlint
npm run preview  # serve the production build
```
