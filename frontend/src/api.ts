import type { Dashboard, JobName } from './types'

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

export interface Api {
  getDashboard(token: string): Promise<Dashboard>
  runJob(token: string, job: JobName): Promise<void>
}

export function createApi(baseUrl: string): Api {
  async function call(path: string, token: string, init: RequestInit = {}): Promise<Response> {
    let response: Response
    try {
      response = await fetch(`${baseUrl}${path}`, { ...init, headers: { Authorization: `Bearer ${token}` } })
    } catch {
      throw new UnavailableError()
    }
    if (response.status === 401) throw new UnauthorizedError()
    if (response.status >= 500) throw new UnavailableError(`Server returned ${response.status}`)
    if (!response.ok) throw new Error(`Request failed: ${response.status}`)
    return response
  }

  return {
    async getDashboard(token) {
      const response = await call('/api/dashboard', token)
      return (await response.json()) as Dashboard
    },
    async runJob(token, job) {
      await call(`/api/run/${job}`, token, { method: 'POST' })
    },
  }
}
