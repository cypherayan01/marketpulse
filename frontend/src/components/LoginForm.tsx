import { useState, type FormEvent } from 'react'
import { LineChart } from 'lucide-react'

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
      <h1><span className="brand-mark" aria-hidden="true"><LineChart size={18} /></span>MarketPulse</h1>
      <p className="muted">Signals, scorecards and your holdings - in one place.</p>
      {message && <p className="warning">{message}</p>}
      <label htmlFor="token">Dashboard token</label>
      <input id="token" type="password" autoComplete="current-password" value={token}
             onChange={(e) => setToken(e.target.value)} />
      <button type="submit">Open dashboard</button>
      <p className="footnote">The DASHBOARD_TOKEN you set on the server. Stored only in this browser.</p>
    </form>
  )
}
