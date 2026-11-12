import { useState } from 'react'
import type { Checkpoint } from '../types'

interface Props {
  title: string
  checkpoints: Checkpoint[]
  keySessions: number
}

const W = 560
const H = 200
const PAD = { top: 16, right: 12, bottom: 28, left: 36 }

const sessionsLabel = (n: number) => `${n} session${n === 1 ? '' : 's'}`
const pts = (v: number) => `${v >= 0 ? '+' : ''}${v.toFixed(2)} pts`

/**
 * Share of calls that were right at each checkpoint, one hue, on one 0-100% axis with a 50% "coin flip" line.
 * Each bar is focusable and has a hover/focus tooltip with the exact figures.
 */
export function HitRateChart({ title, checkpoints, keySessions }: Props) {
  const measured = checkpoints.filter((c) => c.measured > 0)
  const [active, setActive] = useState<number | null>(null)
  const plotW = W - PAD.left - PAD.right
  const plotH = H - PAD.top - PAD.bottom
  const slot = measured.length ? plotW / measured.length : plotW
  const barW = Math.min(36, slot * 0.6)
  const y = (pct: number) => PAD.top + plotH * (1 - pct / 100)

  const label = (c: Checkpoint) =>
    `${sessionsLabel(c.sessions)}: ${Math.round(c.hitRatePct)}% right, edge ${pts(c.avgEdgePct)}, ${c.measured} calls`

  return (
    <figure className="chart" aria-label={title}>
      <svg viewBox={`0 0 ${W} ${H}`} role="list" className="chart-svg">
        {[0, 25, 50, 75, 100].map((t) => (
          <g key={t} aria-hidden="true">
            <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} className={t === 50 ? 'ref-line' : 'grid-line'} />
            <text x={PAD.left - 8} y={y(t) + 4} className="axis-label" textAnchor="end">{t}%</text>
          </g>
        ))}
        <text x={W - PAD.right} y={y(50) - 6} className="ref-label" textAnchor="end">50% = coin flip</text>
        {measured.map((c, i) => {
          const x = PAD.left + slot * i + (slot - barW) / 2
          const top = y(c.hitRatePct)
          const isKey = c.sessions === keySessions
          return (
            <g key={c.sessions} role="listitem" tabIndex={0} aria-label={label(c)}
               onMouseEnter={() => setActive(i)} onMouseLeave={() => setActive(null)}
               onFocus={() => setActive(i)} onBlur={() => setActive(null)} className="bar-group">
              <rect x={PAD.left + slot * i} y={PAD.top} width={slot} height={plotH} className="hit-target" />
              <path className={isKey ? 'bar bar-key' : 'bar'}
                    d={`M${x},${y(0)} V${top + 4} Q${x},${top} ${x + 4},${top} H${x + barW - 4} Q${x + barW},${top} ${x + barW},${top + 4} V${y(0)} Z`} />
              {isKey && (
                <text x={x + barW / 2} y={top - 6} textAnchor="middle" className="bar-label">{Math.round(c.hitRatePct)}%</text>
              )}
              <text x={x + barW / 2} y={H - 8} textAnchor="middle" className="axis-label">{c.sessions}d</text>
            </g>
          )
        })}
      </svg>
      {active !== null && measured[active] && (
        <div className="chart-tooltip" role="status">
          <strong>After {sessionsLabel(measured[active].sessions)}</strong>
          <span>{Math.round(measured[active].hitRatePct)}% of calls right</span>
          <span>Edge vs Nifty {pts(measured[active].avgEdgePct)}</span>
          <span>{measured[active].measured} calls measured</span>
        </div>
      )}
      <figcaption className="footnote">
        Right = a buy beat Nifty, an avoid lagged it, over the same days. Bars: share right after N trading sessions.
      </figcaption>
    </figure>
  )
}
