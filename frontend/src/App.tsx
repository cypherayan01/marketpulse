import { useCallback, useEffect, useState } from 'react'
import { UnauthorizedError, UnavailableError, type Api } from './api'
import { Dashboard } from './components/Dashboard'
import { LoginForm } from './components/LoginForm'
import type { Dashboard as DashboardData, HoldingsView, JobName, NewHolding, OwnedPosition, Performance, PredictionSource, ScorecardView, UniverseEntry } from './types'

const TOKEN_KEY = 'marketpulse.token'
const MAX_WAKE_ATTEMPTS = 12 // ~2 minutes at the default delay; Render usually wakes in about one

type State =
  | { kind: 'login'; message?: string }
  | { kind: 'loading' }
  | { kind: 'waking'; attempt: number }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; data: DashboardData; performance: Performance | null; holdings: HoldingsView | null; universe: UniverseEntry[]; scorecard: ScorecardView | null; predictionDates: string[] }

interface Props {
  api: Api
  /** Wait between retries while the server wakes up. */
  retryDelayMs?: number
}

export default function App({ api, retryDelayMs = 10_000 }: Props) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))
  const [state, setState] = useState<State>(() => (token ? { kind: 'loading' } : { kind: 'login' }))
  const [status, setStatus] = useState<string>()

  const logout = useCallback((message?: string) => {
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
    setState({ kind: 'login', message })
  }, [])

  const load = useCallback(async (currentToken: string): Promise<void> => {
    for (let attempt = 1; ; attempt++) {
      try {
        const data = await api.getDashboard(currentToken)
        // The track record lives in the database; if it can't be read, still show the rest of the dashboard.
        const [performance, holdings, universe, scorecard, predictionDates] = await Promise.all([
          api.getPerformance(currentToken).catch(() => null),
          api.getHoldings(currentToken).catch(() => null),
          api.getUniverse(currentToken).catch(() => [] as UniverseEntry[]),
          api.getScorecard(currentToken, 'LIVE').catch(() => null),
          api.getPredictionDates(currentToken, 'LIVE').catch(() => [] as string[]),
        ])
        setState({ kind: 'ready', data, performance, holdings, universe, scorecard, predictionDates })
        return
      } catch (error) {
        if (error instanceof UnauthorizedError) {
          logout('That token was rejected - check DASHBOARD_TOKEN on the server.')
          return
        }
        if (error instanceof UnavailableError && attempt < MAX_WAKE_ATTEMPTS) {
          setState({ kind: 'waking', attempt })
          await new Promise((resolve) => setTimeout(resolve, retryDelayMs))
          continue
        }
        setState({ kind: 'error', message: error instanceof Error ? error.message : 'Something went wrong' })
        return
      }
    }
  }, [api, logout, retryDelayMs])

  useEffect(() => {
    // Fetching from the backend is syncing with an external system; load() only sets state after awaiting it.
    // oxlint-disable-next-line react/set-state-in-effect
    if (token) void load(token)
  }, [token, load])

  function login(newToken: string) {
    localStorage.setItem(TOKEN_KEY, newToken)
    setState({ kind: 'loading' })
    setToken(newToken)
  }

  async function run(job: JobName) {
    if (!token) return
    try {
      await api.runJob(token, job)
      setStatus(`Started the ${job} job - refresh in about a minute to see the results.`)
    } catch (error) {
      setStatus(`Couldn't start the ${job} job: ${error instanceof Error ? error.message : 'unknown error'}`)
    }
  }

  async function reloadHoldings(currentToken: string) {
    const holdings = await api.getHoldings(currentToken).catch(() => null)
    setState((s) => (s.kind === 'ready' ? { ...s, holdings } : s))
  }

  /** Throws the server's validation message so the form can show it. */
  async function addHolding(holding: NewHolding) {
    if (!token) return
    const saved = await api.addHolding(token, holding)
    await reloadHoldings(token)
    await api.runJob(token, 'holdings').catch(() => undefined)
    setStatus(`Added ${saved.symbol}. Checking your holdings now - refresh in about a minute for the verdict.`)
  }

  async function deleteHolding(id: number) {
    if (!token) return
    await api.deleteHolding(token, id)
    await reloadHoldings(token)
  }

  switch (state.kind) {
    case 'login':
      return <LoginForm onLogin={login} message={state.message} />
    case 'loading':
      return <p className="centered">Loading…</p>
    case 'waking':
      return (
        <p className="centered">
          Waking up the server… (free hosting sleeps when idle, this takes about a minute) · attempt {state.attempt}
        </p>
      )
    case 'error':
      return (
        <div className="centered">
          <p className="warning">{state.message}</p>
          <button type="button" onClick={() => token && load(token)}>Try again</button>
        </div>
      )
    case 'ready':
      return (
        <Dashboard data={state.data} performance={state.performance} holdings={state.holdings}
                   onAddHolding={addHolding} onDeleteHolding={deleteHolding} universe={state.universe}
                   onAnalyze={(symbol: string, owned?: OwnedPosition) => api.analyze(token!, symbol, owned)}
                   onExplain={(symbol: string, owned?: OwnedPosition) => api.explain(token!, symbol, owned)}
                   scorecard={state.scorecard} predictionDates={state.predictionDates}
                   loadScorecard={(source: PredictionSource) => api.getScorecard(token!, source)}
                   loadPredictionDates={(source: PredictionSource) => api.getPredictionDates(token!, source)}
                   loadPredictions={(date: string, source: PredictionSource) => api.getPredictions(token!, date, source)}
                   status={status} onRun={run}
                   onRefresh={() => token && load(token)} onLogout={() => logout()} />
      )
  }
}
