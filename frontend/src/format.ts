const rupeeFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })
const numberFormat = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** ₹1,234.50 (Indian digit grouping). */
export const rupees = (value: number) => rupeeFormat.format(value)

export const number = (value: number) => numberFormat.format(value)

/** +1.13% / -0.77% */
export const signedPct = (value: number) => `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`

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
