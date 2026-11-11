import { istTime, number, signedPct } from '../format'
import type { BriefReport } from '../types'

export function BriefPanel({ brief }: { brief: BriefReport | null }) {
  if (!brief) {
    return <p className="empty">No brief yet - it is written at 8:30 AM IST on trading days.</p>
  }
  return (
    <section className="panel">
      <h2>Pre-market brief</h2>
      <p className="brief">{brief.text}</p>
      <table className="cues">
        <caption>Global cues (last close vs previous)</caption>
        <tbody>
          {brief.cues.map((c) => (
            <tr key={c.symbol}>
              <th scope="row">{c.name}</th>
              <td>{number(c.last)}</td>
              <td className={c.changePct >= 0 ? 'up' : 'down'}>{signedPct(c.changePct)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {brief.missingCues.length > 0 && <p className="footnote">Unavailable: {brief.missingCues.join(', ')}</p>}
      <p className="footnote">{brief.aiWritten ? 'Written by Claude' : 'Numbers only (AI unavailable)'} · {istTime(brief.generatedAt)}</p>
    </section>
  )
}
