import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { UnauthorizedError, UnavailableError, type Api } from './api'
import App from './App'
import { dashboard } from './test/fixtures'

function fakeApi(overrides: Partial<Api> = {}): Api {
  return {
    getDashboard: vi.fn().mockResolvedValue(dashboard),
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

  it('logs out', async () => {
    localStorage.setItem('marketpulse.token', 'secret')
    render(<App api={fakeApi()} />)

    await userEvent.click(await screen.findByRole('button', { name: /log out/i }))

    expect(screen.getByLabelText(/dashboard token/i)).toBeInTheDocument()
    expect(localStorage.getItem('marketpulse.token')).toBeNull()
  })
})
