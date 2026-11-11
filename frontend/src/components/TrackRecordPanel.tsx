import { rupees, shortDate, signedPct, styleName, trendClass } from '../format'
import type { Performance, StyleStats, TradeStatus } from '../types'

const MEANINGFUL_SAMPLE = 30
const STATUS: Record<TradeStatus, string> = {
  OPEN: 'Open', TARGET_HIT: 'Target hit', STOPPED: 'Stopped', EXPIRED: 'Time exit',
}

export function TrackRecordPanel({ performance }: { performance: Performance | null }) {
  if (!performance) {
    return <p className="empty">Track record unavailable - is the database reachable?</p>
  }
  return (
    <>
      <p className="footnote">
        Every recommended setup is followed as a paper trade at the signal price: stop first if a day hits both,
        gaps fill at the open, swing exits after 15 sessions, positional after 120, BTST at the next open.
      </p>
      <div className="cards">
        {performance.styles.map((s) => <StyleCard key={s.style} stats={s} />)}
      </div>

      <section className="panel" aria-label="Open paper trades">
        <h2>Open paper trades ({performance.open.length})</h2>
        {performance.open.length === 0 ? <p className="empty">None open.</p> : (
          <table className="trades">
            <thead><tr><th>Stock</th><th>Since</th><th>Entry</th><th>Stop</th><th>Target</th><th>Days</th><th>Best / worst</th></tr></thead>
            <tbody>
              {performance.open.map((t) => (
                <tr key={t.id}>
                  <td data-label="Stock"><strong>{t.symbol}</strong> <span className="footnote">{styleName(t.style)}</span></td>
                  <td data-label="Since">{shortDate(t.signalDate)}</td>
                  <td data-label="Entry">{rupees(t.entry)}</td>
                  <td data-label="Stop">{rupees(t.stop)}</td>
                  <td data-label="Target">{rupees(t.target)}</td>
                  <td data-label="Days">{t.sessionsHeld}</td>
                  <td data-label="Best / worst">{signedPct(t.maxGainPct)} / {signedPct(t.maxLossPct)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="panel" aria-label="Recently closed">
        <h2>Recently closed</h2>
        {performance.recentClosed.length === 0 ? <p className="empty">Nothing closed yet.</p> : (
          <table className="trades">
            <thead><tr><th>Stock</th><th>Signal</th><th>Exit</th><th>Outcome</th><th>Return</th></tr></thead>
            <tbody>
              {performance.recentClosed.map((t) => (
                <tr key={t.id}>
                  <td data-label="Stock"><strong>{t.symbol}</strong> <span className="footnote">{styleName(t.style)}</span></td>
                  <td data-label="Signal">{shortDate(t.signalDate)} · {t.score}</td>
                  <td data-label="Exit">{t.exitDate && shortDate(t.exitDate)}</td>
                  <td data-label="Outcome">{STATUS[t.status]}</td>
                  <td data-label="Return" className={trendClass(t.returnPct ?? 0)}>{signedPct(t.returnPct ?? 0)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}

function StyleCard({ stats }: { stats: StyleStats }) {
  const name = styleName(stats.style)
  return (
    <section className="card" aria-label={`${name} track record`}>
      <h3>{name}</h3>
      {stats.closed === 0 ? (
        <p className="empty">No closed trades yet · {stats.open} open.</p>
      ) : (
        <>
          <dl className="plan">
            <div><dt>Win rate</dt><dd aria-label="Win rate">{Math.round(stats.winRatePct)}%</dd></div>
            <div><dt>Avg per trade</dt><dd aria-label="Average return per trade" className={trendClass(stats.avgReturnPct)}>{signedPct(stats.avgReturnPct)}</dd></div>
            <div><dt>Closed / open</dt><dd>{stats.closed} / {stats.open}</dd></div>
          </dl>
          <p className="meta">avg win {signedPct(stats.avgWinPct)} · avg loss {signedPct(stats.avgLossPct)}</p>
          <p className="meta">{stats.targetHits} target · {stats.stops} stopped · {stats.expired} expired</p>
          {stats.closed < MEANINGFUL_SAMPLE && (
            <p className="warning">Too few closed trades to judge yet ({stats.closed} of ~{MEANINGFUL_SAMPLE}).</p>
          )}
          <table className="buckets">
            <caption>By score</caption>
            <thead><tr><th>Score</th><th>Trades</th><th>Win</th><th>Avg</th></tr></thead>
            <tbody>
              {stats.byScore.map((b) => (
                <tr key={b.label}><th scope="row">{b.label}</th><td>{b.closed}</td><td>{Math.round(b.winRatePct)}%</td><td>{signedPct(b.avgReturnPct)}</td></tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  )
}
