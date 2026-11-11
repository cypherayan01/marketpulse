import { istTime } from '../format'
import type { JobName, ScanReport } from '../types'
import { SignalCard } from './SignalCard'

interface Props {
  title: string
  job: JobName
  report: ScanReport | null
  onRun: (job: JobName) => void
}

export function ScanPanel({ title, job, report, onRun }: Props) {
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

  return (
    <section className="panel">
      <p className="market">{report.market}</p>
      {report.notes.map((note) => (
        <p key={note} className="warning"><span aria-hidden="true">⚠️ </span><span>{note}</span></p>
      ))}

      <h2>Setups</h2>
      {report.bullish.length === 0 && <p className="empty">No setups this time - sitting out is a valid position.</p>}
      <div className="cards">
        {report.bullish.map((s) => <SignalCard key={s.symbol} signal={s} resultsDate={report.resultsSoon[s.symbol]} />)}
      </div>

      {report.bearish.length > 0 && (
        <>
          <h2>Avoid / review if holding</h2>
          <div className="cards">
            {report.bearish.map((s) => <SignalCard key={s.symbol} signal={s} />)}
          </div>
        </>
      )}

      <p className="footnote">
        {report.scanned} stocks scanned · {istTime(report.generatedAt)}
        {report.failed.length > 0 && ` · data unavailable: ${report.failed.join(', ')}`}
      </p>
      {runButton}
    </section>
  )
}
