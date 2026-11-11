import type { Dashboard, Holding, HoldingsView, JobName, NewHolding, Performance } from './types'

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
  runJob(token: string, job: JobName): Promise<void>
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
    if (response.status === 400) {
      const details = await response.json().catch(() => ({})) as { error?: string }
      throw new ValidationError(details.error ?? 'The server rejected the request')
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
    async runJob(token, job) {
      await call(`/api/run/${job}`, token, { method: 'POST' })
    },
  }
}
