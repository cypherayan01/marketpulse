import { ArrowRight, FileText, Newspaper } from 'lucide-react'
import { signedMoney, trendClass } from '../format'
import type { Dashboard as DashboardData, HoldingsView, JobName, ScorecardView, TradeSignal } from '../types'
import { BriefPanel } from './BriefPanel'

interface Props {
  data: DashboardData
  holdings: HoldingsView | null
  scorecard: ScorecardView | null
  onRun: (job: JobName) => void
  onOpen: (tab: string) => void
}

export function Overview({ data, holdings, scorecard, onRun, onOpen }: Props) {
  const scans = [
    { name: 'Swing', report: data.swing },
    { name: 'Positional', report: data.positional },
    { name: 'BTST', report: data.btst },
  ]
  const alerts = data.news.filter((n) => n.alerted).slice(0, 4)
  const vix = data.brief?.cues.find((c) => c.symbol === '^INDIAVIX')
  const checks = new Map((holdings?.latestCheck?.checks ?? []).map((c) => [c.holding.id, c]))
  const pnl = (holdings?.holdings ?? []).reduce((sum, h) => sum + (checks.get(h.id)?.pnlAmount ?? 0), 0)
  const exits = [...checks.values()].filter((c) => c.verdict === 'EXIT' && holdings?.holdings.some((h) => h.id === c.holding.id)).length
  const swingCard = scorecard?.styles.find((s) => s.style === 'SWING')
  const swingKey = swingCard?.checkpoints.find((c) => c.sessions === swingCard.keySessions)

  return (
    <div className="stack">
      <div className="kpis kpis-hero">
        <div className="kpi">
          <span className="kpi-label">Market</span>
          <span className="kpi-value kpi-text">{data.swing?.market.split(' · ')[0] ?? '-'}</span>
          <span className="kpi-sub">{data.swing?.market.split(' · ')[1] ?? 'run the swing scan for today'}</span>
        </div>
        <div className="kpi">
          <span className="kpi-label">India VIX</span>
          <span className="kpi-value">{vix ? vix.last.toFixed(2) : '-'}</span>
          <span className="kpi-sub">{vix ? (vix.last > 20 ? 'elevated fear' : vix.last < 13 ? 'calm' : 'normal') : 'from the morning brief'}</span>
        </div>
        <div className="kpi">
          <span className="kpi-label">Your holdings</span>
          <span className={`kpi-value ${trendClass(pnl)}`}>{holdings?.holdings.length ? signedMoney(pnl) : '-'}</span>
          <span className="kpi-sub">{holdings?.holdings.length ? `${holdings.holdings.length} stocks · ${exits} to exit` : 'add them under My holdings'}</span>
        </div>
        <div className="kpi">
          <span className="kpi-label">Swing calls right (5 days)</span>
          <span className="kpi-value">{swingKey && swingKey.measured > 0 ? `${Math.round(swingKey.hitRatePct)}%` : '-'}</span>
          <span className="kpi-sub">{swingKey && swingKey.measured > 0 ? `${swingKey.measured} live calls measured` : 'no live results yet'}</span>
        </div>
      </div>

      <div className="grid-2">
        <div className="stack">
          <BriefPanel brief={data.brief} />
          <button type="button" className="secondary" onClick={() => onRun('premarket')}>
            <FileText size={14} aria-hidden="true" /> Write the brief now
          </button>
        </div>
        <div className="stack">
          <section className="panel">
            <h2>Latest picks</h2>
            <ul className="picks">
              {scans.map(({ name, report }) => (
                <li key={name}>
                  <button type="button" className="pick-row" onClick={() => onOpen(name)}>
                    <span className="pick-style">{name}</span>
                    <span className="pick-list">
                      {!report && <span className="muted">not run yet</span>}
                      {report && report.bullish.length === 0 && <span className="muted">no setups</span>}
                      {report?.bullish.slice(0, 4).map((s: TradeSignal) => (
                        <span key={s.symbol} className="chip">{s.symbol} <b>{s.score}</b></span>
                      ))}
                    </span>
                    <ArrowRight size={14} aria-hidden="true" />
                  </button>
                </li>
              ))}
            </ul>
          </section>
          <section className="panel">
            <h2>Recent alerts</h2>
            {alerts.length === 0 && <p className="empty">No market-moving news has crossed the alert line yet.</p>}
            <ul className="alerts">
              {alerts.map((n) => (
                <li key={n.id}>
                  <span className={`impact-badge ${n.direction.toLowerCase()}`}>{n.impactScore}</span>
                  <a href={n.link} target="_blank" rel="noreferrer">{n.title}</a>
                </li>
              ))}
            </ul>
            <button type="button" className="ghost" onClick={() => onOpen('News')}><Newspaper size={14} aria-hidden="true" /> All scored news</button>
          </section>
        </div>
      </div>
    </div>
  )
}
