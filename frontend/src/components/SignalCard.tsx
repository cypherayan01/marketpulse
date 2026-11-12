import { label, price, shortDate } from '../format'
import type { TradeSignal } from '../types'
import { ScoreRing } from './ScoreRing'

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
        <div>
          <h3>{signal.symbol}</h3>
          <p className="meta">
            <span className={`pill pill-${tone}`}>{label(signal.classification)}</span>{' '}
            <span>confidence {signal.confidence.toLowerCase()}</span>
          </p>
        </div>
        <ScoreRing score={signal.score} tone={tone} />
      </header>

      {plan ? (
        <dl className="plan">
          <div><dt>Entry</dt><dd>{price(plan.entry)}</dd></div>
          <div><dt>Stop</dt><dd>{price(plan.stopLoss)}</dd><dd className="risk">-{plan.riskPct.toFixed(1)}%</dd></div>
          <div><dt>Target</dt><dd>{price(plan.target)}</dd></div>
        </dl>
      ) : (
        <p className="meta">Close {price(signal.close)}</p>
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
