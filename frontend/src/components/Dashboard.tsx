import { useState } from 'react'
import { istTime } from '../format'
import type { Dashboard as DashboardData, JobName, Performance } from '../types'
import { BriefPanel } from './BriefPanel'
import { EventsPanel } from './EventsPanel'
import { NewsPanel } from './NewsPanel'
import { ScanPanel } from './ScanPanel'
import { TrackRecordPanel } from './TrackRecordPanel'

const TABS = ['Overview', 'Swing', 'Positional', 'BTST', 'News', 'Events', 'Track record'] as const
type Tab = (typeof TABS)[number]

interface Props {
  data: DashboardData
  performance: Performance | null
  onRun: (job: JobName) => void
  onRefresh: () => void
  onLogout: () => void
  status?: string
}

export function Dashboard({ data, performance, onRun, onRefresh, onLogout, status }: Props) {
  const [tab, setTab] = useState<Tab>('Overview')

  return (
    <div className="app">
      <header className="topbar">
        <h1>MarketPulse</h1>
        <div className="actions">
          <button type="button" className="secondary" onClick={onRefresh}>Refresh</button>
          <button type="button" className="secondary" onClick={onLogout}>Log out</button>
        </div>
      </header>

      <nav role="tablist" aria-label="Sections" className="tabs">
        {TABS.map((t) => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => setTab(t)}>{t}</button>
        ))}
      </nav>

      <p role="status" className="status">{status}</p>

      <main role="tabpanel" aria-label={tab}>
        {tab === 'Overview' && <Overview data={data} />}
        {tab === 'Swing' && <ScanPanel title="Swing" job="eod" report={data.swing} onRun={onRun} />}
        {tab === 'Positional' && <ScanPanel title="Positional" job="positional" report={data.positional} onRun={onRun} />}
        {tab === 'BTST' && <ScanPanel title="BTST" job="btst" report={data.btst} onRun={onRun} />}
        {tab === 'News' && <NewsPanel news={data.news} />}
        {tab === 'Events' && <EventsPanel events={data.events} />}
        {tab === 'Track record' && <TrackRecordPanel performance={performance} />}
      </main>

      <footer className="footnote">
        Updated {istTime(data.serverTime)} · Rule-based signals, not predictions. Not investment advice.
      </footer>
    </div>
  )
}

function Overview({ data }: { data: DashboardData }) {
  const scans = [
    { name: 'Swing', report: data.swing },
    { name: 'Positional', report: data.positional },
    { name: 'BTST', report: data.btst },
  ]
  const alerts = data.news.filter((n) => n.alerted).slice(0, 3)
  return (
    <>
      <BriefPanel brief={data.brief} />
      <section className="panel">
        <h2>Latest picks</h2>
        <ul className="glance">
          {scans.map(({ name, report }) => (
            <li key={name}>
              <strong>{name}:</strong>{' '}
              {report ? (report.bullish.map((s) => `${s.symbol} (${s.score})`).join(', ') || 'no setups') : 'not run yet'}
            </li>
          ))}
        </ul>
      </section>
      {alerts.length > 0 && (
        <section className="panel">
          <h2>Recent alerts</h2>
          <ul className="glance">
            {alerts.map((n) => <li key={n.id}>{n.impactScore}/10 · {n.title}</li>)}
          </ul>
        </section>
      )}
    </>
  )
}
