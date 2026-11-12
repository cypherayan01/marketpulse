import { useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { istTime } from '../format'
import type { NewsEntry } from '../types'

export function NewsPanel({ news, onCheck }: { news: NewsEntry[]; onCheck?: () => void }) {
  const [alertsOnly, setAlertsOnly] = useState(false)
  const shown = alertsOnly ? news.filter((n) => n.alerted) : news

  return (
    <section className="panel">
      <div className="hero-head">
        <label className="toggle">
          <input type="checkbox" checked={alertsOnly} onChange={(e) => setAlertsOnly(e.target.checked)} />
          Alerts only
        </label>
        {onCheck && (
          <button type="button" className="secondary" onClick={onCheck}>
            <RefreshCw size={14} aria-hidden="true" /> Check news now
          </button>
        )}
      </div>
      {shown.length === 0 && <p className="empty">No scored headlines yet.</p>}
      <ul className="news">
        {shown.map((n) => (
          <li key={n.id} className={n.alerted ? 'alerted' : ''}>
            <div className="news-head">
              <span className={`impact ${n.direction.toLowerCase()}`}>{n.impactScore}/10</span>
              <a href={n.link} target="_blank" rel="noreferrer">{n.title}</a>
            </div>
            <p>{n.reason}</p>
            <p className="footnote">
              {n.direction.toLowerCase()} · {[...n.sectors, ...n.stocks].join(', ') || 'broad market'} · {n.source} · {istTime(n.publishedAt)}
            </p>
          </li>
        ))}
      </ul>
    </section>
  )
}
