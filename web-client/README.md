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

The defaults are paths on the app's own origin: in deployment the nginx serves them, in dev
the Vite server proxies them. The dev proxy strips the prefix like the nginx does; its targets are:

| Variable | Default | Example without nginx |
|---|---|---|
| `CHATTR_DEV_CHATS_TARGET` | `http://localhost/chats` | `http://localhost:8080` |
| `CHATTR_DEV_MESSENGER_TARGET` | `http://localhost/messenger` | `http://localhost:8081` |

Prefer the proxy over pointing `VITE_*` at the services directly, since `chats-api` doesn't send
CORS headers. Restart `npm run dev` after changing env files.
