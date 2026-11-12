import { useState, type FormEvent } from 'react'
import { istTime, rupees, SEGMENTS, shortDate, signedMoney, signedPct, trendClass } from '../format'
import type { AnalysisVerdict, Horizon, OwnedPosition, StockAnalysis, UniverseEntry, Verdict } from '../types'
import { SignalCard } from './SignalCard'

interface Props {
  universe: UniverseEntry[]
  onAnalyze: (symbol: string, owned?: OwnedPosition) => Promise<StockAnalysis>
  onExplain: (symbol: string, owned?: OwnedPosition) => Promise<string>
}

const VERDICT: Record<AnalysisVerdict, { label: string; tone: string }> = {
  BUY: { label: 'Buy', tone: 'bullish' },
  BUY_SWING: { label: 'Buy for a swing', tone: 'bullish' },
  ACCUMULATE: { label: 'Accumulate', tone: 'review' },
  WAIT: { label: 'Wait', tone: 'neutral' },
  AVOID: { label: 'Avoid', tone: 'bearish' },
}
const POSITION: Record<Verdict, { label: string; tone: string }> = {
  HOLD: { label: 'Keep', tone: 'bullish' },
  REVIEW: { label: 'Review', tone: 'review' },
  EXIT: { label: 'Exit', tone: 'bearish' },
}

export function AnalyzePanel({ universe, onAnalyze, onExplain }: Props) {
  const [symbol, setSymbol] = useState('')
  const [owns, setOwns] = useState(false)
  const [position, setPosition] = useState({ buyPrice: '', buyDate: '', quantity: '1', horizon: 'SWING' as Horizon, stopLoss: '' })
  const [analysis, setAnalysis] = useState<StockAnalysis>()
  const [asked, setAsked] = useState<{ symbol: string; owned?: OwnedPosition }>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [explanation, setExplanation] = useState<string>()
  const [explaining, setExplaining] = useState(false)

  const setField = (field: keyof typeof position) => (e: { target: { value: string } }) =>
    setPosition((p) => ({ ...p, [field]: e.target.value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    const wanted = symbol.trim().toUpperCase()
    const owned: OwnedPosition | undefined = owns ? {
      quantity: Number(position.quantity), buyPrice: Number(position.buyPrice), buyDate: position.buyDate,
      horizon: position.horizon, stopLoss: position.stopLoss ? Number(position.stopLoss) : null,
    } : undefined
    setBusy(true); setError(undefined); setExplanation(undefined)
    try {
      setAnalysis(await onAnalyze(wanted, owned))
      setAsked({ symbol: wanted, owned })
    } catch (e) {
      setAnalysis(undefined)
      setError(e instanceof Error ? e.message : 'Could not analyse the stock')
    } finally {
      setBusy(false)
    }
  }

  async function explain() {
    if (!asked) return
    setExplaining(true)
    try {
      setExplanation(await onExplain(asked.symbol, asked.owned))
    } catch (e) {
      setExplanation(`AI explanation unavailable: ${e instanceof Error ? e.message : 'unknown error'}`)
    } finally {
      setExplaining(false)
    }
  }

  return (
    <>
      <form className="panel holding-form" onSubmit={submit}>
        <h2>Analyse a stock</h2>
        <label>Stock symbol
          <input list="stock-list" value={symbol} onChange={(e) => setSymbol(e.target.value)} required placeholder="e.g. HDFCBANK" />
        </label>
        <datalist id="stock-list">
          {universe.map((u) => <option key={u.symbol} value={u.symbol}>{u.name}</option>)}
        </datalist>
        <label className="toggle"><input type="checkbox" checked={owns} onChange={(e) => setOwns(e.target.checked)} />I already own this stock</label>
        {owns && (
          <>
            <label>Buy price<input type="number" min="0.01" step="0.01" value={position.buyPrice} onChange={setField('buyPrice')} required /></label>
            <label>Buy date<input type="date" value={position.buyDate} onChange={setField('buyDate')} required /></label>
            <label>Quantity<input type="number" min="1" step="1" value={position.quantity} onChange={setField('quantity')} required /></label>
            <label>Horizon
              <select value={position.horizon} onChange={setField('horizon')}>
                <option value="SWING">Swing</option>
                <option value="POSITIONAL">Positional</option>
              </select>
            </label>
            <label>Your stop loss (optional)<input type="number" min="0.01" step="0.01" value={position.stopLoss} onChange={setField('stopLoss')} /></label>
          </>
        )}
        <button type="submit" disabled={busy}>{busy ? 'Analysing…' : 'Analyse'}</button>
      </form>

      {error && <p role="alert" className="panel warning">{error}</p>}
      {analysis && <Result analysis={analysis} explanation={explanation} explaining={explaining} onExplain={explain} />}
    </>
  )
}

function Result({ analysis: a, explanation, explaining, onExplain }: {
  analysis: StockAnalysis; explanation?: string; explaining: boolean; onExplain: () => void
}) {
  const verdict = VERDICT[a.verdict]
  return (
    <>
      <section className="panel">
        <h2 className="stock-title">{a.symbol} · {a.name}</h2>
        <p className="meta">
          {[a.industry, a.segment && SEGMENTS[a.segment].title].filter(Boolean).join(' · ') || 'Not in Nifty 500'}
        </p>
        <p className="market">
          {rupees(a.price)} <span className={trendClass(a.dayChangePct)}>{signedPct(a.dayChangePct)}</span>
          <span className="footnote"> on {shortDate(a.priceDate)}</span>
        </p>
      </section>

      <section className={`panel card ${verdict.tone}`} aria-label="Verdict">
        <p className="verdict-label">{verdict.label}</p>
        <p>{a.headline}</p>
        <ul className="reasons">{a.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
      </section>

      {a.holding && (
        <section className={`panel card ${POSITION[a.holding.verdict].tone}`} aria-label="Your position">
          <h3>Your position</h3>
          <p className="verdict-label">{POSITION[a.holding.verdict].label}</p>
          <dl className="plan">
            <div><dt>P&L</dt><dd className={trendClass(a.holding.pnlPct)}>{signedPct(a.holding.pnlPct)}</dd><dd className="footnote">{signedMoney(a.holding.pnlAmount)}</dd></div>
            <div><dt>Stop</dt><dd>{rupees(a.holding.stop)}</dd></div>
            <div><dt>Bought</dt><dd>{rupees(a.holding.holding.buyPrice)}</dd></div>
          </dl>
          <p className="footnote">Stop: {a.holding.stopSource}</p>
          <ul className="reasons">{a.holding.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
          {a.holding.notes.map((n) => <p key={n} className="warning">{n}</p>)}
        </section>
      )}

      {a.resultsDate && <p className="panel warning">⚠️ Results on {shortDate(a.resultsDate)}</p>}

      <div className="cards">
        <section className="panel" aria-label="Swing view (days to weeks)">
          <h3 className="list-title">Swing view (days to weeks)</h3>
          {a.swing ? <SignalCard signal={a.swing} /> : <p className="empty">Not enough history for the daily view.</p>}
        </section>
        <section className="panel" aria-label="Positional view (months)">
          <h3 className="list-title">Positional view (months)</h3>
          {a.positional ? <SignalCard signal={a.positional} /> : <p className="empty">{a.positionalUnavailable}</p>}
        </section>
      </div>

      {a.news.length > 0 && (
        <section className="panel">
          <h2>Recent news</h2>
          <ul className="news">
            {a.news.map((n) => (
              <li key={n.id}>
                <div className="news-head"><span className={`impact ${n.direction.toLowerCase()}`}>{n.impactScore}/10</span>
                  <a href={n.link} target="_blank" rel="noreferrer">{n.title}</a></div>
                <p>{n.reason}</p>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="panel">
        <button type="button" className="secondary" onClick={onExplain} disabled={explaining}>
          {explaining ? 'Asking Claude…' : 'Explain with AI'}
        </button>
        <p className="footnote">Claude writes a plain-English summary from these numbers (one API call). Analysed {istTime(a.generatedAt)}.</p>
        {explanation && <p className="brief">{explanation}</p>}
      </section>
    </>
  )
}
