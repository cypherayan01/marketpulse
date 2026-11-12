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

export type Segment = 'LARGE' | 'MID' | 'SMALL' | 'OTHER'

export interface Section {
  segment: Segment
  indexLine: string | null
  scanned: number
  bullish: TradeSignal[]
  bearish: TradeSignal[]
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
  /** Large / mid / small / other Nifty 500 - empty for reports saved before segments existed */
  sections: Section[]
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

export type JobName = 'news' | 'premarket' | 'eod' | 'positional' | 'btst' | 'track' | 'holdings' | 'replay' | 'predictions'

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
  segment: Segment | null
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
  bySegment: Bucket[]
}

export interface Performance {
  styles: StyleStats[]
  open: TrackedSignal[]
  recentClosed: TrackedSignal[]
}

export type Horizon = 'SWING' | 'POSITIONAL'
export type Verdict = 'EXIT' | 'REVIEW' | 'HOLD'

export interface Holding {
  id: number
  symbol: string
  quantity: number
  buyPrice: number
  buyDate: string
  horizon: Horizon
  stopLoss: number | null
  target: number | null
}

export type NewHolding = Omit<Holding, 'id'>

export interface HoldingCheck {
  holding: Holding
  price: number
  priceDate: string
  pnlPct: number
  pnlAmount: number
  stop: number
  stopSource: string
  verdict: Verdict
  score: number
  trend: Classification
  reasons: string[]
  notes: string[]
}

export interface HoldingsView {
  holdings: Holding[]
  latestCheck: { generatedAt: string; session: string; checks: HoldingCheck[]; failed: string[] } | null
}

export type AnalysisVerdict = 'BUY' | 'BUY_SWING' | 'ACCUMULATE' | 'WAIT' | 'AVOID'

export interface StockAnalysis {
  symbol: string
  name: string
  industry: string
  segment: Segment | null
  price: number
  priceDate: string
  dayChangePct: number
  verdict: AnalysisVerdict
  headline: string
  reasons: string[]
  swing: TradeSignal | null
  positional: TradeSignal | null
  positionalUnavailable: string | null
  holding: HoldingCheck | null
  resultsDate: string | null
  news: NewsEntry[]
  generatedAt: string
}

export interface OwnedPosition {
  quantity: number
  buyPrice: number
  buyDate: string
  horizon: Horizon
  stopLoss: number | null
}

export interface UniverseEntry {
  symbol: string
  name: string
  segment: Segment
}

export type PredictionSource = 'LIVE' | 'REPLAY'
export type Call = 'BUY' | 'AVOID'

export interface Prediction {
  id: number
  source: PredictionSource
  style: TradeStyle
  symbol: string
  segment: Segment | null
  signalDate: string
  score: number
  classification: Classification
  call: Call
  price: number
  niftyPrice: number
  newsSentiment: number
  /** stock % change after N sessions, keyed by N */
  returns: Record<string, number>
  niftyReturns: Record<string, number>
  nextOpenPct: number | null
  nextHighPct: number | null
}

export interface Checkpoint { sessions: number; measured: number; hitRatePct: number; avgEdgePct: number; avgReturnPct: number }
export interface Breakdown { label: string; measured: number; hitRatePct: number; avgEdgePct: number }
export interface NextMorning { measured: number; gapUpPct: number; avgOpenPct: number; avgHighPct: number }

export interface StyleScorecard {
  style: TradeStyle
  predictions: number
  checkpoints: Checkpoint[]
  keySessions: number
  byCall: Breakdown[]
  byScore: Breakdown[]
  bySegment: Breakdown[]
  byNews: Breakdown[]
  nextMorning: NextMorning
}

export interface ScorecardView {
  source: PredictionSource
  predictions: number
  firstDate: string | null
  lastDate: string | null
  styles: StyleScorecard[]
  caveat: string | null
}

/** BTST share-price window chosen on the dashboard; null = open end. */
export interface PriceRange {
  minPrice: number | null
  maxPrice: number | null
  label: string // e.g. "₹100 - ₹800", "up to ₹1,500", "any price"
}
