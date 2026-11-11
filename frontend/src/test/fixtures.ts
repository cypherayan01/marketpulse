import type { Dashboard, ScanReport, TradeSignal } from '../types'

export const bullishSignal: TradeSignal = {
  symbol: 'HDFCBANK',
  style: 'SWING',
  score: 86,
  classification: 'STRONG_BULLISH',
  confidence: 'HIGH',
  bullishFactors: ['Above 200-day EMA (long-term uptrend)', 'Broke above 20-day high (₹801.90)'],
  bearishFactors: ['RSI 77 overbought - pullback risk, avoid chasing'],
  plan: { entry: 806.6, stopLoss: 771.47, target: 876.86, riskPct: 4.36 },
  invalidation: 'Daily close back below ₹801.90 (failed breakout)',
  close: 806.6,
}

export const bearishSignal: TradeSignal = {
  symbol: 'NTPC',
  style: 'SWING',
  score: 21,
  classification: 'STRONG_BEARISH',
  confidence: 'HIGH',
  bullishFactors: [],
  bearishFactors: ['Below 200-day EMA (long-term downtrend)', 'Underperformed Nifty by 6.7% over 3 months'],
  plan: null,
  invalidation: null,
  close: 327,
}

export const swingReport: ScanReport = {
  style: 'SWING',
  generatedAt: '2026-11-11T11:00:20Z',
  session: '2026-11-11',
  market: 'Nifty 25,255.05 (+0.84% today) · above 50 & 200-day EMA → supportive for longs',
  bullish: [bullishSignal],
  bearish: [bearishSignal],
  resultsSoon: { HDFCBANK: '2026-11-17' },
  events: [],
  scanned: 50,
  failed: [],
  notes: [],
}

export const dashboard: Dashboard = {
  serverTime: '2026-11-11T12:00:00Z',
  brief: {
    generatedAt: '2026-11-11T03:00:40Z',
    date: '2026-11-11',
    text: 'Mood: Mixed. Strong FII inflows support sentiment.',
    aiWritten: true,
    cues: [
      { name: 'S&P 500', symbol: '^GSPC', last: 7605.63, changePct: 1.13, asOf: '2026-11-10' },
      { name: 'India VIX', symbol: '^INDIAVIX', last: 17.56, changePct: -0.77, asOf: '2026-11-10' },
    ],
    missingCues: ['Gold ($)'],
  },
  swing: swingReport,
  positional: null,
  btst: null,
  news: [
    {
      id: 'n1', title: 'RBI cuts repo rate unexpectedly', source: 'ET Markets', link: 'https://news/1',
      publishedAt: '2026-11-11T05:00:00Z', scoredAt: '2026-11-11T05:05:00Z', impactScore: 9,
      direction: 'BULLISH', sectors: ['Banking'], stocks: ['HDFCBANK'], reason: 'Cheaper credit.', alerted: true,
    },
    {
      id: 'n2', title: 'Crude oil inches up 0.2%', source: 'CNBC', link: 'https://news/2',
      publishedAt: '2026-11-11T04:00:00Z', scoredAt: '2026-11-11T04:05:00Z', impactScore: 3,
      direction: 'NEUTRAL', sectors: ['Energy'], stocks: [], reason: 'Minor move.', alerted: false,
    },
  ],
  events: [
    { date: '2026-12-04', type: 'MACRO', symbol: null, title: 'RBI monetary policy decision', impact: 'HIGH', source: 'CONFIG' },
  ],
}
