import { useState, type FormEvent } from 'react'
import { istTime, rupees, shortDate, signedMoney, signedPct, styleName, trendClass } from '../format'
import type { Holding, HoldingCheck, HoldingsView, Horizon, NewHolding, Verdict } from '../types'

interface Props {
  view: HoldingsView | null
  onAdd: (holding: NewHolding) => Promise<void>
  onDelete: (id: number) => Promise<void>
  onCheck: () => void
}

const VERDICT_ORDER = { EXIT: 0, REVIEW: 1, HOLD: 2 } as const
const VERDICT_TONE: Record<Verdict, string> = { EXIT: 'bearish', REVIEW: 'review', HOLD: 'bullish' }

export function HoldingsPanel({ view, onAdd, onDelete, onCheck }: Props) {
  const [deleteError, setDeleteError] = useState<string>()

  if (!view) {
    return <p className="empty">Holdings unavailable - is the database reachable?</p>
  }
  const checks = new Map((view.latestCheck?.checks ?? []).map((c) => [c.holding.id, c]))
  const sorted = [...view.holdings].sort((a, b) => rank(checks.get(a.id)) - rank(checks.get(b.id)) || a.symbol.localeCompare(b.symbol))
  // The last check can still include holdings removed since, so total only what you still own.
  const totalPnl = view.holdings.reduce((sum, h) => sum + (checks.get(h.id)?.pnlAmount ?? 0), 0)

  async function remove(holding: Holding) {
    setDeleteError(undefined)
    try {
      await onDelete(holding.id)
    } catch (e) {
      setDeleteError(`Couldn't remove ${holding.symbol}: ${e instanceof Error ? e.message : 'unknown error'}`)
    }
  }

  return (
    <>
      {view.holdings.length === 0 ? (
        <p className="empty">Add the stocks you own below - every trading day after the close they get a HOLD / REVIEW / EXIT check.</p>
      ) : (
        <section className="panel">
          {deleteError && <p role="alert" className="warning">{deleteError}</p>}
          <p className="market">
            Total P&L <span aria-label="Total P&L" className={trendClass(totalPnl)}>{signedMoney(totalPnl)}</span>
            {view.latestCheck && <span className="footnote"> · checked {istTime(view.latestCheck.generatedAt)}</span>}
          </p>
          <div className="cards">
            {sorted.map((h) => <HoldingCard key={h.id} holding={h} check={checks.get(h.id)} onRemove={() => remove(h)} />)}
          </div>
          <button type="button" className="secondary" onClick={onCheck}>Check holdings now</button>
        </section>
      )}
      <AddHoldingForm onAdd={onAdd} />
    </>
  )
}

function rank(check?: HoldingCheck) {
  return check ? VERDICT_ORDER[check.verdict] : 3
}

function HoldingCard({ holding, check, onRemove }: { holding: Holding; check?: HoldingCheck; onRemove: () => void }) {
  const tone = check ? VERDICT_TONE[check.verdict] : 'neutral'
  return (
    <article className={`card ${tone}`} aria-label={holding.symbol}>
      <header className="card-head">
        <h3>{holding.symbol}</h3>
        {check && <span className={`verdict ${check.verdict.toLowerCase()}`}>{check.verdict}</span>}
      </header>
      <p className="meta">
        {holding.quantity} @ {rupees(holding.buyPrice)} · {styleName(holding.horizon)} · since {shortDate(holding.buyDate)}
      </p>
      {check ? (
        <>
          <dl className="plan">
            <div><dt>Now</dt><dd>{rupees(check.price)}</dd></div>
            <div><dt>P&L</dt><dd className={trendClass(check.pnlPct)}>{signedPct(check.pnlPct)}</dd><dd className="footnote">{signedMoney(check.pnlAmount)}</dd></div>
            <div><dt>Stop</dt><dd>{rupees(check.stop)}</dd></div>
          </dl>
          <p className="footnote">Stop: {check.stopSource}</p>
          <ul className="reasons">{check.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
          {check.notes.map((n) => <p key={n} className="warning">{n}</p>)}
        </>
      ) : (
        <p className="empty">Not checked yet - the check runs after the close, or use "Check holdings now".</p>
      )}
      <button type="button" className="remove" aria-label={`Remove ${holding.symbol}`} onClick={onRemove}>Remove</button>
    </article>
  )
}

const EMPTY_FORM = { symbol: '', quantity: '', buyPrice: '', buyDate: '', horizon: 'SWING' as Horizon, stopLoss: '', target: '' }

function AddHoldingForm({ onAdd }: { onAdd: (holding: NewHolding) => Promise<void> }) {
  const [form, setForm] = useState(EMPTY_FORM)
  const [error, setError] = useState<string>()
  const [saving, setSaving] = useState(false)

  const set = (field: keyof typeof EMPTY_FORM) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: e.target.value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(undefined)
    setSaving(true)
    try {
      await onAdd({
        symbol: form.symbol.trim().toUpperCase(),
        quantity: Number(form.quantity),
        buyPrice: Number(form.buyPrice),
        buyDate: form.buyDate,
        horizon: form.horizon,
        stopLoss: form.stopLoss ? Number(form.stopLoss) : null,
        target: form.target ? Number(form.target) : null,
      })
      setForm(EMPTY_FORM)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not add the holding')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="panel holding-form" onSubmit={submit}>
      <h2>Add a holding</h2>
      {error && <p role="alert" className="warning">{error}</p>}
      <label>Symbol<input value={form.symbol} onChange={set('symbol')} required placeholder="e.g. TCS" /></label>
      <label>Quantity<input type="number" min="1" step="1" value={form.quantity} onChange={set('quantity')} required /></label>
      <label>Buy price<input type="number" min="0.01" step="0.01" value={form.buyPrice} onChange={set('buyPrice')} required /></label>
      <label>Buy date<input type="date" value={form.buyDate} onChange={set('buyDate')} required /></label>
      <label>Horizon
        <select value={form.horizon} onChange={set('horizon')}>
          <option value="SWING">Swing</option>
          <option value="POSITIONAL">Positional</option>
        </select>
      </label>
      <p className="footnote hint">Swing: held days to weeks, judged on the daily chart. Positional: held for months, judged on the weekly chart.</p>
      <label>Stop loss (optional)<input type="number" min="0.01" step="0.01" value={form.stopLoss} onChange={set('stopLoss')} /></label>
      <label>Target (optional)<input type="number" min="0.01" step="0.01" value={form.target} onChange={set('target')} /></label>
      <button type="submit" disabled={saving}>Add holding</button>
    </form>
  )
}
