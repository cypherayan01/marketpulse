import type { Dashboard, HoldingsView, Performance, Prediction, ScanReport, ScorecardView, StockAnalysis, StyleScorecard, TradeSignal } from '../types'

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
  sections: [],
}

const smallRiser: TradeSignal = { ...bullishSignal, symbol: 'AARTIIND', score: 88, close: 412.5,
  plan: { entry: 412.5, stopLoss: 395, target: 447.5, riskPct: 4.24 }, invalidation: 'Daily close below ₹395.00 (stop loss)' }

export const segmentedReport: ScanReport = {
  ...swingReport,
  bullish: [smallRiser, bullishSignal],
  sections: [
    { segment: 'LARGE', indexLine: 'Nifty 100 +0.62% today', scanned: 100, bullish: [bullishSignal], bearish: [bearishSignal] },
    { segment: 'SMALL', indexLine: null, scanned: 98, bullish: [smallRiser], bearish: [] },
  ],
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

const emptyStats = (style: 'SWING' | 'POSITIONAL' | 'BTST') => ({
  style, open: 0, closed: 0, wins: 0, winRatePct: 0, avgReturnPct: 0, avgWinPct: 0, avgLossPct: 0,
  targetHits: 0, stops: 0, expired: 0, bestPct: 0, worstPct: 0, byScore: [], byConfidence: [], bySegment: [],
})

export const performance: Performance = {
  styles: [
    {
      style: 'SWING', open: 1, closed: 4, wins: 2, winRatePct: 50, avgReturnPct: 0.5, avgWinPct: 5.5, avgLossPct: -4.5,
      targetHits: 1, stops: 2, expired: 1, bestPct: 10, worstPct: -5,
      byScore: [{ label: '80-100', closed: 2, winRatePct: 50, avgReturnPct: 2.5 }, { label: '65-79', closed: 2, winRatePct: 50, avgReturnPct: -1.5 }],
      byConfidence: [{ label: 'HIGH', closed: 4, winRatePct: 50, avgReturnPct: 0.5 }],
      bySegment: [{ label: 'Large cap', closed: 3, winRatePct: 66.7, avgReturnPct: 2.1 }, { label: 'Small cap', closed: 1, winRatePct: 0, avgReturnPct: -4.3 }],
    },
    emptyStats('POSITIONAL'),
    emptyStats('BTST'),
  ],
  open: [{
    id: 2, style: 'SWING', symbol: 'AXISBANK', signalDate: '2026-11-11', score: 85, classification: 'STRONG_BULLISH',
    confidence: 'HIGH', entry: 1336.4, stop: 1275.33, target: 1458.55, status: 'OPEN', exitDate: null, exitPrice: null,
    returnPct: null, maxGainPct: 2.1, maxLossPct: -0.8, sessionsHeld: 3, segment: 'LARGE',
  }],
  recentClosed: [{
    id: 1, style: 'SWING', symbol: 'HDFCBANK', signalDate: '2026-11-01', score: 86, classification: 'STRONG_BULLISH',
    confidence: 'HIGH', entry: 100, stop: 95, target: 110, status: 'TARGET_HIT', exitDate: '2026-11-08', exitPrice: 110,
    returnPct: 10, maxGainPct: 11, maxLossPct: -1, sessionsHeld: 5, segment: 'LARGE',
  }],
}

const tcs = { id: 1, symbol: 'TCS', quantity: 5, buyPrice: 3100, buyDate: '2026-10-01', horizon: 'POSITIONAL' as const, stopLoss: null, target: 3600 }
const axis = { id: 2, symbol: 'AXISBANK', quantity: 20, buyPrice: 1400, buyDate: '2026-11-03', horizon: 'SWING' as const, stopLoss: 1340, target: null }
const infy = { id: 3, symbol: 'INFY', quantity: 10, buyPrice: 1150, buyDate: '2026-11-11', horizon: 'SWING' as const, stopLoss: null, target: null }

export const holdingsView: HoldingsView = {
  holdings: [axis, infy, tcs],
  latestCheck: {
    generatedAt: '2026-11-11T11:05:00Z',
    session: '2026-11-11',
    failed: [],
    checks: [
      {
        holding: axis, price: 1336.4, priceDate: '2026-11-11', pnlPct: -4.54, pnlAmount: -1272, stop: 1340,
        stopSource: 'your stop', verdict: 'EXIT', score: 40, trend: 'BEARISH',
        reasons: ['Closed below your stop ₹1,340.00'], notes: [],
      },
      {
        holding: tcs, price: 3300, priceDate: '2026-11-11', pnlPct: 6.45, pnlAmount: 1000, stop: 3150,
        stopSource: 'trailing stop, 3 ATR below the highest close since you bought', verdict: 'HOLD', score: 78,
        trend: 'BULLISH', reasons: ['Above 40-week average (long-term uptrend)'], notes: [],
      },
    ],
  },
}

export const stockAnalysis: StockAnalysis = {
  symbol: 'HDFCBANK', name: 'HDFC Bank Ltd.', industry: 'Financial Services', segment: 'LARGE',
  price: 806.6, priceDate: '2026-11-11', dayChangePct: 1.06,
  verdict: 'BUY', headline: 'Buy - short-term and long-term trends agree',
  reasons: ['Swing (days-weeks): strong bullish, score 86', 'Positional (months): bullish, score 78', 'Broke above 20-day high (₹801.90)'],
  swing: bullishSignal,
  positional: { ...bullishSignal, style: 'POSITIONAL', score: 78, classification: 'BULLISH', invalidation: 'Weekly close below ₹740.00 (stop loss)' },
  positionalUnavailable: null,
  holding: null,
  resultsDate: '2026-11-17',
  news: [dashboard.news[0]],
  generatedAt: '2026-11-12T11:30:00Z',
}

const checkpoints = (hits: number[]) => [1, 2, 3, 5, 10, 20, 40, 60].map((sessions, i) => ({
  sessions, measured: hits[i] === undefined ? 0 : 40, hitRatePct: hits[i] ?? 0,
  avgEdgePct: hits[i] === undefined ? 0 : (hits[i] - 50) / 20, avgReturnPct: 1,
}))

const swingCard: StyleScorecard = {
  style: 'SWING', predictions: 240, checkpoints: checkpoints([54, 55, 56, 58, 57, 53]), keySessions: 5,
  byCall: [{ label: 'Buy', measured: 120, hitRatePct: 61, avgEdgePct: 1.4 }, { label: 'Avoid', measured: 120, hitRatePct: 55, avgEdgePct: 0.4 }],
  byScore: [{ label: 'Buy 80+', measured: 60, hitRatePct: 64, avgEdgePct: 1.9 }, { label: 'Buy 65-79', measured: 60, hitRatePct: 52, avgEdgePct: 0.2 }],
  bySegment: [{ label: 'Large cap', measured: 80, hitRatePct: 60, avgEdgePct: 1.1 }, { label: 'Small cap', measured: 80, hitRatePct: 49, avgEdgePct: -0.3 }],
  byNews: [{ label: 'No news', measured: 240, hitRatePct: 58, avgEdgePct: 0.9 }],
  nextMorning: { measured: 0, gapUpPct: 0, avgOpenPct: 0, avgHighPct: 0 },
}

export const liveScorecard: ScorecardView = {
  source: 'LIVE', predictions: 300, firstDate: '2026-11-11', lastDate: '2026-11-12', caveat: null,
  styles: [
    swingCard,
    { ...swingCard, style: 'POSITIONAL', predictions: 0, checkpoints: checkpoints([]), keySessions: 20, byCall: [], byScore: [], bySegment: [], byNews: [] },
    { ...swingCard, style: 'BTST', predictions: 60, checkpoints: checkpoints([57]), keySessions: 1,
      nextMorning: { measured: 60, gapUpPct: 62, avgOpenPct: 0.45, avgHighPct: 1.6 } },
  ],
}

export const replayScorecard: ScorecardView = {
  ...liveScorecard, source: 'REPLAY', predictions: 18000, firstDate: '2025-11-12',
  caveat: "Replayed from history: an estimate. It uses today's index lists.",
}

export const dayPredictions: Prediction[] = [
  { id: 1, source: 'LIVE', style: 'SWING', symbol: 'HDFCBANK', segment: 'LARGE', signalDate: '2026-11-11', score: 86,
    classification: 'STRONG_BULLISH', call: 'BUY', price: 806.6, niftyPrice: 25255.05, newsSentiment: 1,
    returns: { '1': 1.2, '5': 3.5 }, niftyReturns: { '1': 0.2, '5': 1.0 }, nextOpenPct: 0.4, nextHighPct: 1.5 },
  { id: 2, source: 'LIVE', style: 'SWING', symbol: 'NTPC', segment: 'LARGE', signalDate: '2026-11-11', score: 21,
    classification: 'STRONG_BEARISH', call: 'AVOID', price: 327, niftyPrice: 25255.05, newsSentiment: 0,
    returns: { '1': 0.9 }, niftyReturns: { '1': 0.2 }, nextOpenPct: 0.1, nextHighPct: 0.6 },
]
