import { useState, type ComponentType } from 'react'
import {
  Briefcase, CalendarDays, CalendarRange, LayoutDashboard, LineChart, LogOut, Newspaper, RefreshCw, Search, Target,
  Trophy, TrendingUp, Zap,
} from 'lucide-react'
import { istTime, signedPct, trendClass } from '../format'
import type {
  Dashboard as DashboardData, HoldingsView, JobName, NewHolding, OwnedPosition, Performance, Prediction,
  PredictionSource, PriceRange, ScorecardView, StockAnalysis, UniverseEntry,
} from '../types'
import { AnalyzePanel } from './AnalyzePanel'
import { EventsPanel } from './EventsPanel'
import { HoldingsPanel } from './HoldingsPanel'
import { NewsPanel } from './NewsPanel'
import { Overview } from './Overview'
import { PredictionsPanel } from './PredictionsPanel'
import { PriceRangeForm } from './PriceRangeForm'
import { ScanPanel } from './ScanPanel'
import { TrackRecordPanel } from './TrackRecordPanel'

const TABS: { name: string; icon: ComponentType<{ size?: number }> }[] = [
  { name: 'Overview', icon: LayoutDashboard },
  { name: 'Analyse a stock', icon: Search },
  { name: 'My holdings', icon: Briefcase },
  { name: 'Swing', icon: TrendingUp },
  { name: 'Positional', icon: CalendarRange },
  { name: 'BTST', icon: Zap },
  { name: 'Predictions', icon: Target },
  { name: 'Track record', icon: Trophy },
  { name: 'News', icon: Newspaper },
  { name: 'Events', icon: CalendarDays },
]

/** Cues shown in the ticker strip, in this order, when the brief has them. */
const TICKER = ['Nifty 50 (prev close)', 'Bank Nifty (prev close)', 'India VIX', 'S&P 500', 'Brent crude ($)', 'USD/INR']

interface Props {
  data: DashboardData
  performance: Performance | null
  holdings: HoldingsView | null
  onAddHolding: (holding: NewHolding) => Promise<void>
  onDeleteHolding: (id: number) => Promise<void>
  universe: UniverseEntry[]
  onAnalyze: (symbol: string, owned?: OwnedPosition) => Promise<StockAnalysis>
  onExplain: (symbol: string, owned?: OwnedPosition) => Promise<string>
  scorecard: ScorecardView | null
  predictionDates: string[]
  loadScorecard: (source: PredictionSource) => Promise<ScorecardView>
  loadPredictionDates: (source: PredictionSource) => Promise<string[]>
  loadPredictions: (date: string, source: PredictionSource) => Promise<Prediction[]>
  btstRange: PriceRange | null
  onSaveBtstRange: (minPrice: number | null, maxPrice: number | null) => Promise<PriceRange>
  onRun: (job: JobName) => void
  onRefresh: () => void
  onLogout: () => void
  status?: string
}

export function Dashboard(props: Props) {
  const { data, onRun, onRefresh, onLogout, status } = props
  const [tab, setTab] = useState('Overview')
  const [btstRange, setBtstRange] = useState<PriceRange | null>(props.btstRange)
  async function saveBtstRange(minPrice: number | null, maxPrice: number | null) {
    const saved = await props.onSaveBtstRange(minPrice, maxPrice)
    setBtstRange(saved)
    return saved
  }
  const cues = TICKER.map((n) => data.brief?.cues.find((c) => c.name === n)).filter((c) => c !== undefined)

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true"><LineChart size={18} /></span>
          <h1>MarketPulse</h1>
        </div>
        <nav role="tablist" aria-label="Sections" aria-orientation="vertical" className="nav">
          {TABS.map(({ name, icon: Icon }) => (
            <button key={name} type="button" role="tab" aria-selected={tab === name} onClick={() => setTab(name)}>
              <Icon size={16} aria-hidden="true" /><span>{name}</span>
            </button>
          ))}
        </nav>
        <div className="sidebar-foot">
          <button type="button" className="ghost" onClick={onRefresh}><RefreshCw size={15} aria-hidden="true" /> Refresh</button>
          <button type="button" className="ghost" onClick={onLogout}><LogOut size={15} aria-hidden="true" /> Log out</button>
        </div>
      </aside>

      <div className="content">
        {cues.length > 0 && (
          <div className="ticker" aria-label="Markets">
            {cues.map((c) => (
              <span key={c.symbol} className="tick">
                <span className="tick-name">{c.name.replace(' (prev close)', '')}</span>
                <span className="tick-value">{c.last.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>
                <span className={`tick-change ${trendClass(c.changePct)}`}>{signedPct(c.changePct)}</span>
              </span>
            ))}
            {data.brief && <span className="tick-asof">as of the brief, {istTime(data.brief.generatedAt)}</span>}
          </div>
        )}
        <p role="status" className="status">{status}</p>

        <main role="tabpanel" aria-label={tab} className="page">
          {tab === 'Overview' && <Overview data={data} holdings={props.holdings} scorecard={props.scorecard} onRun={onRun} onOpen={setTab} />}
          {tab === 'Analyse a stock' && <AnalyzePanel universe={props.universe} onAnalyze={props.onAnalyze} onExplain={props.onExplain} />}
          {tab === 'My holdings' && (
            <HoldingsPanel view={props.holdings} onAdd={props.onAddHolding} onDelete={props.onDeleteHolding} onCheck={() => onRun('holdings')} />
          )}
          {tab === 'Swing' && <ScanPanel title="Swing" job="eod" report={data.swing} onRun={onRun} />}
          {tab === 'Positional' && <ScanPanel title="Positional" job="positional" report={data.positional} onRun={onRun} />}
          {tab === 'BTST' && (
            <>
              <PriceRangeForm range={btstRange} onSave={saveBtstRange} onRunScan={() => onRun('btst')} />
              <ScanPanel title="BTST" job="btst" report={data.btst} onRun={onRun} priceRange={btstRange} />
            </>
          )}
          {tab === 'Predictions' && (
            <PredictionsPanel initial={props.scorecard} dates={props.predictionDates} loadScorecard={props.loadScorecard}
                              loadDates={props.loadPredictionDates} loadDay={props.loadPredictions} onRunReplay={() => onRun('replay')} />
          )}
          {tab === 'Track record' && <TrackRecordPanel performance={props.performance} />}
          {tab === 'News' && <NewsPanel news={data.news} onCheck={() => onRun('news')} />}
          {tab === 'Events' && <EventsPanel events={data.events} />}
        </main>

        <footer className="footnote page-foot">
          Updated {istTime(data.serverTime)} · Rule-based signals, not predictions of certainty. Not investment advice.
        </footer>
      </div>
    </div>
  )
}
