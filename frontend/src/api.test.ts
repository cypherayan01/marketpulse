import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApi, UnauthorizedError, UnavailableError, ValidationError } from './api'
import { dashboard } from './test/fixtures'

describe('api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('sends the token as a bearer header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(dashboard), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await createApi('https://api.example').getDashboard('secret')

    expect(result.swing?.bullish[0].symbol).toBe('HDFCBANK')
    expect(fetchMock).toHaveBeenCalledWith('https://api.example/api/dashboard',
      expect.objectContaining({ headers: { Authorization: 'Bearer secret' } }))
  })

  it('turns 401 into UnauthorizedError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 401 })))

    await expect(createApi('').getDashboard('bad')).rejects.toBeInstanceOf(UnauthorizedError)
  })

  it('treats network failures and 5xx as a sleeping server', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(createApi('').getDashboard('t')).rejects.toBeInstanceOf(UnavailableError)

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })))
    await expect(createApi('').getDashboard('t')).rejects.toBeInstanceOf(UnavailableError)
  })

  it('fetches the track record', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ styles: [], open: [], recentClosed: [] })))
    vi.stubGlobal('fetch', fetchMock)

    const result = await createApi('https://api.example').getPerformance('secret')

    expect(result.styles).toEqual([])
    expect(fetchMock).toHaveBeenCalledWith('https://api.example/api/performance',
      expect.objectContaining({ headers: { Authorization: 'Bearer secret' } }))
  })

  it('adds a holding and reports the server’s validation message', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'quantity must be greater than 0' }), { status: 400 }))
    vi.stubGlobal('fetch', fetchMock)

    const added = createApi('https://api.example').addHolding('secret', {
      symbol: 'TCS', quantity: 0, buyPrice: 1, buyDate: '2026-11-11', horizon: 'SWING', stopLoss: null, target: null,
    })

    await expect(added).rejects.toEqual(new ValidationError('quantity must be greater than 0'))
    expect(fetchMock).toHaveBeenCalledWith('https://api.example/api/holdings', expect.objectContaining({
      method: 'POST',
      headers: { Authorization: 'Bearer secret', 'Content-Type': 'application/json' },
    }))
  })

  it('deletes a holding', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await createApi('https://api.example').deleteHolding('secret', 3)

    expect(fetchMock).toHaveBeenCalledWith('https://api.example/api/holdings/3', expect.objectContaining({ method: 'DELETE' }))
  })

  it('starts a job with POST', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)

    await createApi('https://api.example').runJob('secret', 'eod')

    expect(fetchMock).toHaveBeenCalledWith('https://api.example/api/run/eod',
      expect.objectContaining({ method: 'POST' }))
  })
})
