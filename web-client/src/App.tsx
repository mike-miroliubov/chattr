import { LoginView } from './login/LoginView.tsx'
import { logout, useSession } from './session/session.ts'

function App() {
  const { userName, connectionStatus } = useSession()

  if (!userName) {
    return <LoginView />
  }

  // Placeholder until the chat list is in place
  return (
    <main>
      <p>Logged in as <strong>{userName}</strong> ({connectionStatus})</p>
      <button onClick={logout}>Log out</button>
    </main>
  )
}

export default App
