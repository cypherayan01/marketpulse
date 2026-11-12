import { useState } from 'react'
import { History, Play, Radio } from 'lucide-react'
import { longDate, SEGMENTS, styleName } from '../format'
import type { Breakdown, Prediction, PredictionSource, ScorecardView, StyleScorecard } from '../types'
import { HitRateChart } from './HitRateChart'

interface Props {
  initial: ScorecardView | null
  dates: string[]
  loadScorecard: (source: PredictionSource) => Promise<ScorecardView>
  loadDates: (source: PredictionSource) => Promise<string[]>
  loadDay: (date: string, source: PredictionSource) => Promise<Prediction[]>
  onRunReplay: () => void
}

const pts = (v: number) => `${v >= 0 ? '+' : ''}${v.toFixed(2)} pts`
const signed = (v: number) => `${v >= 0 ? '+' : ''}${v.toFixed(2)}`
const count = (n: number) => n.toLocaleString('en-IN')
const sessions = (n: number) => `${n} session${n === 1 ? '' : 's'}`

export function PredictionsPanel({ initial, dates: initialDates, loadScorecard, loadDates, loadDay, onRunReplay }: Props) {
  const [source, setSource] = useState<PredictionSource>('LIVE')
  const [card, setCard] = useState<ScorecardView | null>(initial)
  const [dates, setDates] = useState<string[]>(initialDates)
  const [day, setDay] = useState('')
  const [predictions, setPredictions] = useState<Prediction[]>([])
  const [error, setError] = useState<string>()

  async function switchTo(next: PredictionSource) {
    if (next === source) return
    setSource(next); setDay(''); setPredictions([]); setError(undefined)
    try {
      const [scorecard, nextDates] = await Promise.all([loadScorecard(next), loadDates(next)])
      setCard(scorecard); setDates(nextDates)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load predictions')
    }
  }

  async function pickDay(date: string) {
    setDay(date)
    if (!date) { setPredictions([]); return }
    try {
      setPredictions(await loadDay(date, source))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load that day')
    }
  }

  const empty = !card || card.predictions === 0

  return (
    <div className="stack">
      <section className="panel hero-panel">
        <div className="hero-head">
          <div>
            <h2 className="page-title">Prediction scorecard</h2>
            <p className="muted">Every buy and avoid call, checked against Nifty after 1 to 60 trading sessions.</p>
          </div>
          <div className="segmented" role="group" aria-label="Which predictions">
            <button type="button" aria-pressed={source === 'LIVE'} onClick={() => switchTo('LIVE')}>
              <Radio size={14} aria-hidden="true" /> Live predictions
            </button>
            <button type="button" aria-pressed={source === 'REPLAY'} onClick={() => switchTo('REPLAY')}>
              <History size={14} aria-hidden="true" /> History replay (estimate)
            </button>
          </div>
        </div>
        {card?.caveat && <p className="note"><span aria-hidden="true">ⓘ </span>{card.caveat}</p>}
        {error && <p role="alert" className="warning">{error}</p>}
        {card && !empty && (
          <p className="muted">
            {count(card.predictions)} predictions · {card.firstDate && longDate(card.firstDate)} – {card.lastDate && longDate(card.lastDate)}
          </p>
        )}
        {empty && (
          <div className="empty-state">
            <p>
              {source === 'LIVE'
                ? 'No live predictions yet - every scan records its calls from now on, and they are checked as days pass.'
                : 'No replay yet.'}
            </p>
            <p className="muted">Want an answer today? The history replay re-runs the scans over the past year (about 5 minutes).</p>
            <button type="button" onClick={onRunReplay}><Play size={14} aria-hidden="true" /> Run the history replay</button>
          </div>
        )}
      </section>

      {card && !empty && card.styles.map((s) => <StyleSection key={s.style} card={s} />)}

      {card && !empty && source === 'REPLAY' && (
        <button type="button" className="secondary" onClick={onRunReplay}><History size={14} aria-hidden="true" /> Re-run the history replay</button>
      )}

      <section className="panel">
        <div className="hero-head">
          <h2>What was predicted on a day</h2>
          <label className="inline-field">Predictions made on
            <select value={day} onChange={(e) => pickDay(e.target.value)}>
              <option value="">Choose a day</option>
              {dates.map((d) => <option key={d} value={d}>{longDate(d)}</option>)}
            </select>
          </label>
        </div>
        {day && predictions.length === 0 && <p className="empty">No predictions on that day.</p>}
        {predictions.length > 0 && <DayTable predictions={predictions} />}
      </section>
    </div>
  )
}

function StyleSection({ card }: { card: StyleScorecard }) {
  const name = styleName(card.style)
  const key = card.checkpoints.find((c) => c.sessions === card.keySessions)
  const measured = card.checkpoints.some((c) => c.measured > 0)
  return (
    <section className="panel" aria-label={`${name} predictions`}>
      <div className="hero-head">
        <h2>{name}</h2>
        <span className="muted">{count(card.predictions)} predictions</span>
      </div>
      {!measured ? (
        <p className="empty">No {card.style === 'BTST' ? name : name.toLowerCase()} predictions have been measured yet.</p>
      ) : (
        <>
          <div className="kpis">
            <div className="kpi">
              <span className="kpi-label">Right after {sessions(card.keySessions)}</span>
              <span className="kpi-value" aria-label={`Right after ${sessions(card.keySessions)}`}>{Math.round(key?.hitRatePct ?? 0)}%</span>
              <span className="kpi-sub">{key?.measured ?? 0} calls measured</span>
            </div>
            <div className="kpi">
              <span className="kpi-label">Average edge vs Nifty</span>
              <span className={`kpi-value ${(key?.avgEdgePct ?? 0) >= 0 ? 'pos' : 'neg'}`} aria-label="Average edge vs Nifty">{pts(key?.avgEdgePct ?? 0)}</span>
              <span className="kpi-sub">after {sessions(card.keySessions)}</span>
            </div>
            {card.style === 'BTST' && card.nextMorning.measured > 0 && (
              <>
                <div className="kpi">
                  <span className="kpi-label">Opened higher next morning</span>
                  <span className="kpi-value" aria-label="Opened higher next morning">{Math.round(card.nextMorning.gapUpPct)}%</span>
                  <span className="kpi-sub">avg open {signed(card.nextMorning.avgOpenPct)}%</span>
                </div>
                <div className="kpi">
                  <span className="kpi-label">Average next-day high</span>
                  <span className="kpi-value" aria-label="Average next-day high">{signed(card.nextMorning.avgHighPct)}%</span>
                  <span className="kpi-sub">best point to sell</span>
                </div>
              </>
            )}
          </div>
          <HitRateChart title={`${name}: share of calls right at each checkpoint`} checkpoints={card.checkpoints} keySessions={card.keySessions} />
          <div className="breakdowns">
            <BreakdownTable caption="By call" rows={card.byCall} sessionsAt={card.keySessions} />
            <BreakdownTable caption="By score" rows={card.byScore} sessionsAt={card.keySessions} />
            <BreakdownTable caption="By market cap" rows={card.bySegment} sessionsAt={card.keySessions} />
            <BreakdownTable caption="By news" rows={card.byNews} sessionsAt={card.keySessions} />
          </div>
        </>
      )}
    </section>
  )
}

/** Meter list: name, hit-rate bar against 50%, figures - bars aligned so groups compare at a glance. */
function BreakdownTable({ caption, rows, sessionsAt }: { caption: string; rows: Breakdown[]; sessionsAt: number }) {
  if (rows.length === 0) return null
  return (
    <table className="meter-table">
      <caption>{caption} <span className="muted">· after {sessions(sessionsAt)}</span></caption>
      <thead><tr><th>Group</th><th>Right</th><th>Edge</th><th>Calls</th></tr></thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.label}>
            <th scope="row">{r.label}</th>
            <td>
              <span className="meter" aria-hidden="true"><span className="meter-fill" style={{ width: `${r.hitRatePct}%` }} /><span className="meter-mid" /></span>
              {Math.round(r.hitRatePct)}%
            </td>
            <td className={r.avgEdgePct >= 0 ? 'pos' : 'neg'}>{signed(r.avgEdgePct)}</td>
            <td className="muted">{count(r.measured)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

const DAY_CHECKPOINTS = [1, 5, 20]

function DayTable({ predictions }: { predictions: Prediction[] }) {
  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr><th>Stock</th><th>Style</th><th>Call</th><th>Score</th>
            {DAY_CHECKPOINTS.map((k) => <th key={k}>Edge {k}d</th>)}<th>Next open</th></tr>
        </thead>
        <tbody>
          {predictions.map((p) => (
            <tr key={p.id}>
              <th scope="row">{p.symbol}<span className="muted small">{p.segment ? ` · ${SEGMENTS[p.segment].title}` : ''}</span></th>
              <td>{styleName(p.style)}</td>
              <td><span className={`pill ${p.call === 'BUY' ? 'pill-buy' : 'pill-avoid'}`}>{p.call === 'BUY' ? 'Buy' : 'Avoid'}</span></td>
              <td>{p.score}</td>
              {DAY_CHECKPOINTS.map((k) => {
                const stock = p.returns[String(k)]
                const nifty = p.niftyReturns[String(k)]
                if (stock === undefined || nifty === undefined) return <td key={k} className="muted">-</td>
                const edge = p.call === 'BUY' ? stock - nifty : nifty - stock
                return (
                  <td key={k} className={edge > 0 ? 'pos' : 'neg'}
                      aria-label={`${edge > 0 ? 'Right' : 'Wrong'} after ${sessions(k)}`}>{signed(edge)}</td>
                )
              })}
              <td className="muted">{p.nextOpenPct == null ? '-' : `${signed(p.nextOpenPct)}%`}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
