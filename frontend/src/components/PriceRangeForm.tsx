import { useState, type FormEvent } from 'react'
import { ValidationError } from '../api'
import type { PriceRange } from '../types'

interface Props {
  range: PriceRange | null
  onSave: (minPrice: number | null, maxPrice: number | null) => Promise<PriceRange>
  onRunScan: () => void
}

const toText = (n: number | null | undefined) => (n == null ? '' : String(n))
const toNumber = (text: string) => (text.trim() === '' ? null : Number(text))

/** BTST share-price window: only stocks you can afford get scanned in detail and listed. */
export function PriceRangeForm({ range, onSave, onRunScan }: Props) {
  const [min, setMin] = useState(toText(range?.minPrice))
  const [max, setMax] = useState(toText(range?.maxPrice))
  const [saved, setSaved] = useState<PriceRange | null>(null)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function save(minPrice: number | null, maxPrice: number | null) {
    setSaving(true)
    setError('')
    try {
      const result = await onSave(minPrice, maxPrice)
      setMin(toText(result.minPrice))
      setMax(toText(result.maxPrice))
      setSaved(result)
    } catch (e) {
      setSaved(null)
      setError(e instanceof ValidationError ? e.message : 'Could not save the range - try again.')
    } finally {
      setSaving(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void save(toNumber(min), toNumber(max))
  }

  const current = saved ?? range
  return (
    <form className="panel price-range" onSubmit={submit} aria-label="BTST price range">
      <div className="price-range-head">
        <h2>Price range</h2>
        <span className="pill">{current && !(current.minPrice == null && current.maxPrice == null) ? current.label : 'any price'}</span>
      </div>
      <p className="footnote">
        Only stocks priced inside this range are scanned and listed (up to 10 strong setups per market cap).
        Leave a box empty for no limit.
      </p>
      <div className="price-range-fields">
        <label>Minimum price (₹)
          <input type="number" min="1" step="any" inputMode="decimal" value={min} onChange={(e) => setMin(e.target.value)} placeholder="no minimum" />
        </label>
        <label>Maximum price (₹)
          <input type="number" min="1" step="any" inputMode="decimal" value={max} onChange={(e) => setMax(e.target.value)} placeholder="no maximum" />
        </label>
        <button type="submit" disabled={saving}>Save range</button>
        <button type="button" className="secondary" disabled={saving}
                onClick={() => { setMin(''); setMax(''); void save(null, null) }}>Clear</button>
      </div>
      {error && <p role="alert" className="warning">{error}</p>}
      {saved && (
        <div role="status" className="status">
          Saved: {saved.label}. The 14:50 scan uses it from now on; stocks the last scan skipped appear after the next scan.{' '}
          <button type="button" className="secondary" onClick={onRunScan}>Run BTST scan now with this range</button>
        </div>
      )}
    </form>
  )
}
