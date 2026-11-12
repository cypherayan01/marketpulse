/** Score 0-100 as a ring; the number is the label, the ring is decoration. */
export function ScoreRing({ score, tone }: { score: number; tone: string }) {
  const r = 18
  const c = 2 * Math.PI * r
  return (
    <span className={`score-ring ${tone}`}>
      <svg viewBox="0 0 44 44" aria-hidden="true">
        <circle cx="22" cy="22" r={r} className="ring-track" />
        <circle cx="22" cy="22" r={r} className="ring-value" strokeDasharray={`${(score / 100) * c} ${c}`} />
      </svg>
      <span className="score" aria-label="score">{score}</span>
    </span>
  )
}
