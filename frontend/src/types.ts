// Mirrors the JSON returned by GET /api/dashboard (Java records in com.marketpulse.*).

export type Classification = 'STRONG_BULLISH' | 'BULLISH' | 'NEUTRAL' | 'BEARISH' | 'STRONG_BEARISH'
export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW'
export type TradeStyle = 'BTST' | 'SWING' | 'POSITIONAL'
export type Direction = 'BULLISH' | 'BEARISH' | 'MIXED' | 'NEUTRAL'
export type Impact = 'HIGH' | 'MEDIUM' | 'LOW'

export interface TradePlan {
  entry: number
  stopLoss: number
  target: number
  riskPct: number
}

export interface TradeSignal {
  symbol: string
  style: TradeStyle
  score: number
  classification: Classification
  confidence: Confidence
  bullishFactors: string[]
  bearishFactors: string[]
  plan: TradePlan | null
  invalidation: string | null
  close: number
}

export interface MarketEvent {
  date: string // yyyy-MM-dd
  type: 'MACRO' | 'RESULTS'
  symbol: string | null
  title: string
  impact: Impact
  source: 'CONFIG' | 'NEWS'
}

export interface ScanReport {
  style: TradeStyle
  generatedAt: string // ISO instant
  session: string
  market: string
  bullish: TradeSignal[]
  bearish: TradeSignal[]
  resultsSoon: Record<string, string>
  events: MarketEvent[]
  scanned: number
  failed: string[]
  notes: string[]
}

export interface CueReading {
  name: string
  symbol: string
  last: number
  changePct: number
  asOf: string
}

export interface BriefReport {
  generatedAt: string
  date: string
  text: string
  aiWritten: boolean
  cues: CueReading[]
  missingCues: string[]
}

export interface NewsEntry {
  id: string
  title: string
  source: string
  link: string
  publishedAt: string
  scoredAt: string
  impactScore: number
  direction: Direction
  sectors: string[]
  stocks: string[]
  reason: string
  alerted: boolean
}

export interface Dashboard {
  serverTime: string
  brief: BriefReport | null
  swing: ScanReport | null
  positional: ScanReport | null
  btst: ScanReport | null
  news: NewsEntry[]
  events: MarketEvent[]
}

export type JobName = 'news' | 'premarket' | 'eod' | 'positional' | 'btst' | 'track'

export type TradeStatus = 'OPEN' | 'TARGET_HIT' | 'STOPPED' | 'EXPIRED'

export interface TrackedSignal {
  id: number
  style: TradeStyle
  symbol: string
  signalDate: string
  score: number
  classification: Classification
  confidence: Confidence
  entry: number
  stop: number
  target: number
  status: TradeStatus
  exitDate: string | null
  exitPrice: number | null
  returnPct: number | null
  maxGainPct: number
  maxLossPct: number
  sessionsHeld: number
}

export interface Bucket {
  label: string
  closed: number
  winRatePct: number
  avgReturnPct: number
}

export interface StyleStats {
  style: TradeStyle
  open: number
  closed: number
  wins: number
  winRatePct: number
  avgReturnPct: number
  avgWinPct: number
  avgLossPct: number
  targetHits: number
  stops: number
  expired: number
  bestPct: number
  worstPct: number
  byScore: Bucket[]
  byConfidence: Bucket[]
}

export interface Performance {
  styles: StyleStats[]
  open: TrackedSignal[]
  recentClosed: TrackedSignal[]
}
