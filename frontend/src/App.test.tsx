import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { UnauthorizedError, UnavailableError, type Api } from './api'
import App from './App'
import { dashboard, holdingsView, liveScorecard, performance, stockAnalysis } from './test/fixtures'

function fakeApi(overrides: Partial<Api> = {}): Api {
  return {
    getDashboard: vi.fn().mockResolvedValue(dashboard),
    getPerformance: vi.fn().mockResolvedValue(performance),
    getHoldings: vi.fn().mockResolvedValue(holdingsView),
    addHolding: vi.fn().mockResolvedValue({ ...holdingsView.holdings[0], id: 9, symbol: 'HDFCBANK' }),
    deleteHolding: vi.fn().mockResolvedValue(undefined),
    getUniverse: vi.fn().mockResolvedValue([{ symbol: 'HDFCBANK', name: 'HDFC Bank Ltd.', segment: 'LARGE' }]),
    analyze: vi.fn().mockResolvedValue(stockAnalysis),
    explain: vi.fn().mockResolvedValue('Explained.'),
    getScorecard: vi.fn().mockResolvedValue(liveScorecard),
    getPredictionDates: vi.fn().mockResolvedValue(['2026-11-11']),
    getPredictions: vi.fn().mockResolvedValue([]),
    runJob: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
}

describe('App', () => {
  it('asks for the dashboard token first', () => {
    render(<App api={fakeApi()} />)

    expect(screen.getByLabelText(/dashboard token/i)).toBeInTheDocument()
  })

  it('remembers the token and loads the dashboard', async () => {
    const api = fakeApi()
    render(<App api={api} />)

    await userEvent.type(screen.getByLabelText(/dashboard token/i), 'secret')
    await userEvent.click(screen.getByRole('button', { name: /open dashboard/i }))

    expect(await screen.findByText(/Strong FII inflows/)).toBeInTheDocument()
    expect(api.getDashboard).toHaveBeenCalledWith('secret')
    expect(localStorage.getItem('marketpulse.token')).toBe('secret')
  })

  it('goes back to the login screen when the token is rejected', async () => {
    localStorage.setItem('marketpulse.token', 'old')
    render(<App api={fakeApi({ getDashboard: vi.fn().mockRejectedValue(new UnauthorizedError()) })} />)

    expect(await screen.findByText(/token was rejected/i)).toBeInTheDocument()
    expect(localStorage.getItem('marketpulse.token')).toBeNull()
  })

  it('keeps retrying while the free-tier server wakes up', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    const getDashboard = vi.fn()
      .mockRejectedValueOnce(new UnavailableError())
      .mockResolvedValue(dashboard)
    render(<App api={fakeApi({ getDashboard })} retryDelayMs={10} />)

    expect(await screen.findByText(/waking up the server/i)).toBeInTheDocument()
    expect(await screen.findByText(/Strong FII inflows/)).toBeInTheDocument()
    expect(getDashboard).toHaveBeenCalledTimes(2)
  })

  it('switches between tabs', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    render(<App api={fakeApi()} />)

    await userEvent.click(await screen.findByRole('tab', { name: 'Swing' }))

    expect(screen.getByRole('heading', { name: 'HDFCBANK' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Swing' })).toHaveAttribute('aria-selected', 'true')
  })

  it('starts a job and says what happens next', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    const api = fakeApi()
    render(<App api={api} />)

    await userEvent.click(await screen.findByRole('tab', { name: 'Positional' }))
    await userEvent.click(screen.getByRole('button', { name: /run positional scan now/i }))

    await waitFor(() => expect(api.runJob).toHaveBeenCalledWith('secret', 'positional'))
    expect(screen.getByRole('status')).toHaveTextContent(/started/i)
  })

  it('shows the paper-trading track record', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    const api = fakeApi()
    render(<App api={api} />)

    await userEvent.click(await screen.findByRole('tab', { name: 'Track record' }))

    expect(screen.getByRole('region', { name: 'Swing track record' })).toBeInTheDocument()
    expect(api.getPerformance).toHaveBeenCalledWith('secret')
  })

  it('adds a holding, reloads the list and starts a check', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    const api = fakeApi()
    render(<App api={api} />)

    await userEvent.click(await screen.findByRole('tab', { name: 'My holdings' }))
    await userEvent.type(screen.getByLabelText('Symbol'), 'HDFCBANK')
    await userEvent.type(screen.getByLabelText('Quantity'), '15')
    await userEvent.type(screen.getByLabelText('Buy price'), '806.6')
    await userEvent.type(screen.getByLabelText('Buy date'), '2026-11-11')
    await userEvent.click(screen.getByRole('button', { name: 'Add holding' }))

    await waitFor(() => expect(api.runJob).toHaveBeenCalledWith('secret', 'holdings'))
    expect(api.addHolding).toHaveBeenCalledWith('secret', expect.objectContaining({ symbol: 'HDFCBANK' }))
    expect(api.getHoldings).toHaveBeenCalledTimes(2) // initial load + after adding
    expect(screen.getByRole('status')).toHaveTextContent(/added HDFCBANK/i)
  })

  it('analyses a stock from its own tab', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    const api = fakeApi()
    render(<App api={api} />)

    await userEvent.click(await screen.findByRole('tab', { name: 'Analyse a stock' }))
    await userEvent.type(screen.getByLabelText('Stock symbol'), 'HDFCBANK')
    await userEvent.click(screen.getByRole('button', { name: 'Analyse' }))

    expect(await screen.findByRole('region', { name: 'Verdict' })).toBeInTheDocument()
    expect(api.analyze).toHaveBeenCalledWith('secret', 'HDFCBANK', undefined)
  })

  it('shows the prediction scorecard', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    render(<App api={fakeApi()} />)

    await userEvent.click(await screen.findByRole('tab', { name: 'Predictions' }))

    expect(screen.getByRole('region', { name: 'Swing predictions' })).toBeInTheDocument()
  })

  it('starts the news check from the News tab', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    const api = fakeApi()
    render(<App api={api} />)

    await userEvent.click(await screen.findByRole('tab', { name: 'News' }))
    await userEvent.click(screen.getByRole('button', { name: /check news now/i }))

    await waitFor(() => expect(api.runJob).toHaveBeenCalledWith('secret', 'news'))
  })

  it('logs out', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    render(<App api={fakeApi()} />)

    await userEvent.click(await screen.findByRole('button', { name: /log out/i }))

    expect(screen.getByLabelText(/dashboard token/i)).toBeInTheDocument()
    expect(localStorage.getItem('marketpulse.token')).toBeNull()
  })
})
