import { useState, type FormEvent } from 'react'

interface Props {
  onLogin: (token: string) => void
  message?: string
}

export function LoginForm({ onLogin, message }: Props) {
  const [token, setToken] = useState('')

  function submit(event: FormEvent) {
    event.preventDefault()
    if (token.trim()) onLogin(token.trim())
  }

  return (
    <form className="login" onSubmit={submit}>
      <h1>MarketPulse</h1>
      {message && <p className="warning">{message}</p>}
      <label htmlFor="token">Dashboard token</label>
      <input id="token" type="password" autoComplete="current-password" value={token}
             onChange={(e) => setToken(e.target.value)} />
      <button type="submit">Open dashboard</button>
      <p className="footnote">The DASHBOARD_TOKEN you set on the server. Stored only in this browser.</p>
    </form>
  )
}
