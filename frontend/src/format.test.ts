import { describe, expect, it } from 'vitest'
import { signedMoney, signedPct, styleName, trendClass } from './format'

describe('format', () => {
  it('never shows a minus sign on a value that rounds to zero', () => {
    expect(signedPct(-0.000003)).toBe('0.00%')
    expect(signedPct(0)).toBe('0.00%')
    expect(signedPct(1.234)).toBe('+1.23%')
    expect(signedPct(-0.77)).toBe('-0.77%')
  })

  it('formats signed rupee amounts the same way', () => {
    expect(signedMoney(-0.004)).toBe('₹0.00')
    expect(signedMoney(1216)).toBe('+₹1,216.00')
    expect(signedMoney(-1272)).toBe('-₹1,272.00')
  })

  it('colours only real gains and losses', () => {
    expect(trendClass(-0.000003)).toBe('flat')
    expect(trendClass(0.5)).toBe('up')
    expect(trendClass(-0.5)).toBe('down')
  })

  it('keeps BTST in capitals', () => {
    expect(styleName('BTST')).toBe('BTST')
    expect(styleName('SWING')).toBe('Swing')
    expect(styleName('POSITIONAL')).toBe('Positional')
  })
})
