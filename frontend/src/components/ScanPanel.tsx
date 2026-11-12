import { useState } from 'react'
import { istTime, SEGMENTS } from '../format'
import type { JobName, PriceRange, ScanReport, Section, Segment, TradeSignal } from '../types'
import { SignalCard } from './SignalCard'

interface Props {
  title: string
  job: JobName
  report: ScanReport | null
  onRun: (job: JobName) => void
  /** BTST: show only setups priced inside this range (narrowing applies at once, widening needs a new scan). */
  priceRange?: PriceRange | null
}

function inRange(range: PriceRange | null | undefined) {
  return (s: TradeSignal) =>
    !range || ((range.minPrice == null || s.close >= range.minPrice) && (range.maxPrice == null || s.close <= range.maxPrice))
}

export function ScanPanel({ title, job, report, onRun, priceRange }: Props) {
  const [filter, setFilter] = useState<Segment | 'ALL'>('ALL')
  const runButton = (
    <button type="button" className="secondary" onClick={() => onRun(job)}>Run {title.toLowerCase()} scan now</button>
  )

  if (!report) {
    return (
      <section className="panel">
        <p className="empty">The {title.toLowerCase()} scan hasn't run since the server started. It runs on schedule, or start it now (this also sends the report to Telegram/email).</p>
        {runButton}
      </section>
    )
  }

  const keep = inRange(priceRange)
  const sections = (report.sections ?? []).map((s) => ({ ...s, bullish: s.bullish.filter(keep) }))
  const shown = filter === 'ALL' ? sections : sections.filter((s) => s.segment === filter)
  const allSetups = report.sections?.length ? report.sections.flatMap((s) => s.bullish) : report.bullish
  const keptSetups = allSetups.filter(keep).length
  const filtering = priceRange != null && (priceRange.minPrice != null || priceRange.maxPrice != null)

  return (
    <>
      <section className="panel">
        <p className="market">{report.market}</p>
        {filtering && keptSetups < allSetups.length && (
          <p className="footnote">Showing {keptSetups} of {allSetups.length} setups priced {priceRange.label}</p>
        )}
        {report.notes.map((note) => (
          <p key={note} className="warning"><span aria-hidden="true">⚠️ </span><span>{note}</span></p>
        ))}
        {sections.length > 0 && (
          <div className="chips" aria-label="Market cap">
            <button type="button" aria-pressed={filter === 'ALL'} onClick={() => setFilter('ALL')}>All caps</button>
            {sections.map((s) => (
              <button key={s.segment} type="button" aria-pressed={filter === s.segment} onClick={() => setFilter(s.segment)}>
                {SEGMENTS[s.segment].title}
              </button>
            ))}
          </div>
        )}
        {sections.length === 0 && (
          <SignalLists bullish={report.bullish.filter(keep)} bearish={report.bearish} resultsSoon={report.resultsSoon} />
        )}
      </section>

      {shown.map((s) => <SegmentSection key={s.segment} section={s} resultsSoon={report.resultsSoon} />)}

      <section className="panel">
        <p className="footnote">
          {report.scanned} stocks scanned · {istTime(report.generatedAt)}
          {report.failed.length > 0 && ` · ${report.failed.length} skipped (new listings or no data)`}
        </p>
        {runButton}
      </section>
    </>
  )
}

function SegmentSection({ section, resultsSoon }: { section: Section; resultsSoon: Record<string, string> }) {
  const segment = SEGMENTS[section.segment]
  return (
    <section className="panel" aria-label={segment.title}>
      <h2><span aria-hidden="true">{segment.icon} </span>{segment.title}</h2>
      <p className="footnote">{section.indexLine ?? segment.index} · {section.scanned} scanned</p>
      <SignalLists bullish={section.bullish} bearish={section.bearish} resultsSoon={resultsSoon} />
    </section>
  )
}

function SignalLists({ bullish, bearish, resultsSoon }: {
  bullish: TradeSignal[]; bearish: TradeSignal[]; resultsSoon: Record<string, string>
}) {
  return (
    <>
      <h3 className="list-title">Setups</h3>
      {bullish.length === 0 && <p className="empty">No setups this time - sitting out is a valid position.</p>}
      <div className="cards">
        {bullish.map((s) => <SignalCard key={s.symbol} signal={s} resultsDate={resultsSoon[s.symbol]} />)}
      </div>
      {bearish.length > 0 && (
        <>
          <h3 className="list-title">Avoid / review if holding</h3>
          <div className="cards">
            {bearish.map((s) => <SignalCard key={s.symbol} signal={s} />)}
          </div>
        </>
      )}
    </>
  )
}
