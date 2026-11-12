import type { Dashboard, Holding, HoldingsView, JobName, NewHolding, OwnedPosition, Performance, Prediction, PredictionSource, PriceRange, ScorecardView, StockAnalysis, UniverseEntry } from './types'

/** The dashboard token was missing or wrong. */
export class UnauthorizedError extends Error {
  constructor() {
    super('Unauthorized')
    this.name = 'UnauthorizedError'
  }
}

/** The server didn't answer - on Render's free tier it is usually waking up (takes ~1 minute). */
export class UnavailableError extends Error {
  constructor(message = 'Server unavailable') {
    super(message)
    this.name = 'UnavailableError'
  }
}

/** The server rejected the input; {@link message} says why (shown to the user as-is). */
export class ValidationError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ValidationError'
  }
}

export interface Api {
  getDashboard(token: string): Promise<Dashboard>
  getPerformance(token: string): Promise<Performance>
  getHoldings(token: string): Promise<HoldingsView>
  addHolding(token: string, holding: NewHolding): Promise<Holding>
  deleteHolding(token: string, id: number): Promise<void>
  getUniverse(token: string): Promise<UniverseEntry[]>
  analyze(token: string, symbol: string, owned?: OwnedPosition): Promise<StockAnalysis>
  explain(token: string, symbol: string, owned?: OwnedPosition): Promise<string>
  getScorecard(token: string, source: PredictionSource): Promise<ScorecardView>
  getPredictionDates(token: string, source: PredictionSource): Promise<string[]>
  getPredictions(token: string, date: string, source: PredictionSource): Promise<Prediction[]>
  runJob(token: string, job: JobName): Promise<void>
  getBtstPriceRange(token: string): Promise<PriceRange>
  saveBtstPriceRange(token: string, minPrice: number | null, maxPrice: number | null): Promise<PriceRange>
}

export function createApi(baseUrl: string): Api {
  async function call(path: string, token: string, init: RequestInit = {}, body?: unknown): Promise<Response> {
    const headers: Record<string, string> = { Authorization: `Bearer ${token}` }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    let response: Response
    try {
      response = await fetch(`${baseUrl}${path}`, {
        ...init, headers, ...(body !== undefined && { body: JSON.stringify(body) }),
      })
    } catch {
      throw new UnavailableError()
    }
    if (response.status === 401) throw new UnauthorizedError()
    if (response.status === 400 || response.status === 404) {
      const details = await response.json().catch(() => ({})) as { error?: string }
      throw new ValidationError(details.error ?? (response.status === 404 ? 'Not found' : 'The server rejected the request'))
    }
    if (response.status >= 500) throw new UnavailableError(`Server returned ${response.status}`)
    if (!response.ok) throw new Error(`Request failed: ${response.status}`)
    return response
  }

  return {
    async getDashboard(token) {
      const response = await call('/api/dashboard', token)
      return (await response.json()) as Dashboard
    },
    async getPerformance(token) {
      const response = await call('/api/performance', token)
      return (await response.json()) as Performance
    },
    async getHoldings(token) {
      const response = await call('/api/holdings', token)
      return (await response.json()) as HoldingsView
    },
    async addHolding(token, holding) {
      const response = await call('/api/holdings', token, { method: 'POST' }, holding)
      return (await response.json()) as Holding
    },
    async deleteHolding(token, id) {
      await call(`/api/holdings/${id}`, token, { method: 'DELETE' })
    },
    async getUniverse(token) {
      const response = await call('/api/universe', token)
      return (await response.json()) as UniverseEntry[]
    },
    async analyze(token, symbol, owned) {
      const response = await call(analysisPath(symbol, owned), token)
      return (await response.json()) as StockAnalysis
    },
    async explain(token, symbol, owned) {
      const path = analysisPath(symbol, owned).replace('?', '/explain?').replace(/^([^?]*)$/, '$1/explain')
      const response = await call(path, token, { method: 'POST' })
      return ((await response.json()) as { text: string }).text
    },
    async getScorecard(token, source) {
      return (await (await call(`/api/predictions/scorecard?source=${source}`, token)).json()) as ScorecardView
    },
    async getPredictionDates(token, source) {
      return (await (await call(`/api/predictions/dates?source=${source}`, token)).json()) as string[]
    },
    async getPredictions(token, date, source) {
      return (await (await call(`/api/predictions?date=${date}&source=${source}`, token)).json()) as Prediction[]
    },
    async runJob(token, job) {
      await call(`/api/run/${job}`, token, { method: 'POST' })
    },
    async getBtstPriceRange(token) {
      return (await (await call('/api/settings/btst', token)).json()) as PriceRange
    },
    async saveBtstPriceRange(token, minPrice, maxPrice) {
      const response = await call('/api/settings/btst', token, { method: 'PUT' }, { minPrice, maxPrice })
      return (await response.json()) as PriceRange
    },
  }
}

/** /api/analyze/{symbol} with your position (if any) as query parameters. */
function analysisPath(symbol: string, owned?: OwnedPosition) {
  const path = `/api/analyze/${encodeURIComponent(symbol)}`
  if (!owned) return path
  const params = new URLSearchParams({
    buyPrice: String(owned.buyPrice), buyDate: owned.buyDate, quantity: String(owned.quantity), horizon: owned.horizon,
  })
  if (owned.stopLoss != null) params.set('stopLoss', String(owned.stopLoss))
  return `${path}?${params}`
}
