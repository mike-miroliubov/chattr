import { useState, type FormEvent } from 'react'
import { login } from '../session/session.ts'
import './LoginView.css'

export function LoginView() {
  const [userName, setUserName] = useState('')
  const trimmed = userName.trim()

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    if (trimmed) {
      login(trimmed)
    }
  }

  return (
    <form className="login" onSubmit={handleSubmit}>
      <h1>Chattr</h1>
      <p>Please log in</p>
      <label>
        Login
        <input
          value={userName}
          onChange={(e) => setUserName(e.target.value)}
          autoFocus
          autoComplete="username"
        />
      </label>
      <button type="submit" disabled={!trimmed}>Log in</button>
    </form>
  )
}
