import { weekdayDate } from '../format'
import type { MarketEvent } from '../types'

export function EventsPanel({ events }: { events: MarketEvent[] }) {
  if (events.length === 0) {
    return <p className="empty">No events in the next 30 days.</p>
  }
  return (
    <section className="panel">
      <ul className="events">
        {events.map((e) => (
          <li key={`${e.date}-${e.title}`}>
            <span className="date">{weekdayDate(e.date)}</span>
            <span className="title">{e.title}</span>
            <span className={`badge ${e.impact.toLowerCase()}`}>{e.impact}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
