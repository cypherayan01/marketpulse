const rupeeFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })
const numberFormat = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** ₹1,234.50 (Indian digit grouping). */
export const rupees = (value: number) => rupeeFormat.format(value)

export const number = (value: number) => numberFormat.format(value)

/** Rounds to 2 decimals and turns -0 into 0, so tiny negative values don't show as "-0.00". */
const round2 = (value: number) => Math.round(value * 100) / 100 || 0

/** +1.13% / -0.77% / 0.00% */
export function signedPct(value: number) {
  const r = round2(value)
  return `${r > 0 ? '+' : ''}${r.toFixed(2)}%`
}

/** +₹1,216.00 / -₹1,272.00 / ₹0.00 */
export function signedMoney(value: number) {
  const r = round2(value)
  return `${r > 0 ? '+' : r < 0 ? '-' : ''}${rupees(Math.abs(r))}`
}

/** CSS class for a gain/loss: 'up', 'down', or 'flat' when it rounds to zero. */
export function trendClass(value: number) {
  const r = round2(value)
  return r > 0 ? 'up' : r < 0 ? 'down' : 'flat'
}

/** Trading style as people write it: Swing, Positional, BTST. */
export function styleName(style: string) {
  return style === 'BTST' ? 'BTST' : label(style)
}

/** "17 Nov" from "2026-11-17" (a calendar date, no time zone shifts). */
export function shortDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
    .toLocaleDateString('en-GB', { day: '2-digit', month: 'short', timeZone: 'UTC' })
}

/** "Fri 04 Dec" from "2026-12-04". */
export function weekdayDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
    .toLocaleDateString('en-GB', { weekday: 'short', day: '2-digit', month: 'short', timeZone: 'UTC' })
    .replace(',', '')
}

/** "11 Nov, 4:30 pm IST" from an ISO instant. */
export function istTime(isoInstant: string) {
  return new Date(isoInstant).toLocaleString('en-IN', {
    day: '2-digit', month: 'short', hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata',
  }) + ' IST'
}

/** "STRONG_BULLISH" → "Strong bullish" */
export function label(value: string) {
  const words = value.toLowerCase().replace('_', ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}
