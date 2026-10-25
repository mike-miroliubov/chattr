# web-client

Browser SPA for Chattr (React + TypeScript + Vite). Feature-parity target: `cli-client`.

## Development
Requires Node 20.19+ (or 22.12+).
```
npm install
npm run dev      # http://localhost:5173
```
In dev, `/chats/*` and `/messenger/*` (incl. WebSocket) are proxied to the backend nginx at
`http://localhost` (override with `CHATTR_BACKEND_URL`). Start the backend from `local-dev/backend-simple`.

## Other scripts
```
npm run build    # type-check + production build into dist/
npm run lint     # oxlint
npm run preview  # serve the production build
```
