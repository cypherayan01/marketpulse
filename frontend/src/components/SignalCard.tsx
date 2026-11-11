import { label, rupees, shortDate } from '../format'
import type { TradeSignal } from '../types'

interface Props {
  signal: TradeSignal
  /** Results date if it falls inside the holding window. */
  resultsDate?: string
}

export function SignalCard({ signal, resultsDate }: Props) {
  const tone = signal.classification.includes('BULLISH') ? 'bullish' : signal.classification.includes('BEARISH') ? 'bearish' : 'neutral'
  const reasons = tone === 'bearish' ? signal.bearishFactors : signal.bullishFactors
  const warnings = tone === 'bearish' ? [] : signal.bearishFactors
  const plan = signal.plan

  return (
    <article className={`card ${tone}`}>
      <header className="card-head">
        <h3>{signal.symbol}</h3>
        <span className="score" aria-label="score">{signal.score}</span>
      </header>
      <p className="meta">
        <span>{label(signal.classification)}</span> · <span>confidence {signal.confidence.toLowerCase()}</span>
      </p>

      {plan ? (
        <dl className="plan">
          <div><dt>Entry</dt><dd>{rupees(plan.entry)}</dd></div>
          <div><dt>Stop</dt><dd>{rupees(plan.stopLoss)}</dd><dd className="risk">-{plan.riskPct.toFixed(1)}%</dd></div>
          <div><dt>Target</dt><dd>{rupees(plan.target)}</dd></div>
        </dl>
      ) : (
        <p className="meta">Close {rupees(signal.close)}</p>
      )}

      <ul className="reasons">
        {reasons.map((r) => <li key={r}>{r}</li>)}
      </ul>
      {warnings.length > 0 && (
        <ul className="warnings">
          {warnings.map((w) => <li key={w}><span aria-hidden="true">⚠️ </span>{w}</li>)}
        </ul>
      )}
      {resultsDate && <p className="warning">⚠️ Results on {shortDate(resultsDate)} - a results surprise can break any setup</p>}
      {signal.invalidation && <p className="invalidation">✋ {signal.invalidation}</p>}
    </article>
  )
}
