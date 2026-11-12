import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { holdingsView, stockAnalysis } from '../test/fixtures'
import { AnalyzePanel } from './AnalyzePanel'

function setup(overrides: Partial<Parameters<typeof AnalyzePanel>[0]> = {}) {
  const props = {
    universe: [{ symbol: 'HDFCBANK', name: 'HDFC Bank Ltd.', segment: 'LARGE' as const }],
    onAnalyze: vi.fn().mockResolvedValue(stockAnalysis),
    onExplain: vi.fn().mockResolvedValue('HDFC Bank looks strong on both horizons.'),
    ...overrides,
  }
  render(<AnalyzePanel {...props} />)
  return props
}

async function analyse(symbol: string) {
  await userEvent.type(screen.getByLabelText('Stock symbol'), symbol)
  await userEvent.click(screen.getByRole('button', { name: 'Analyse' }))
}

describe('AnalyzePanel', () => {
  it('gives a verdict for a stock you are thinking of buying', async () => {
    const { onAnalyze } = setup()

    await analyse('hdfcbank')

    expect(onAnalyze).toHaveBeenCalledWith('HDFCBANK', undefined)
    const verdict = await screen.findByRole('region', { name: 'Verdict' })
    expect(within(verdict).getByText('Buy')).toBeInTheDocument()
    expect(within(verdict).getByText('Buy - short-term and long-term trends agree')).toBeInTheDocument()
    expect(within(verdict).getByText(/strong bullish, score 86/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /HDFCBANK · HDFC Bank Ltd\./ })).toBeInTheDocument()
    expect(screen.getByText(/Financial Services · Large cap/)).toBeInTheDocument()
  })

  it('shows the swing and positional views side by side', async () => {
    setup()
    await analyse('HDFCBANK')

    expect(await screen.findByRole('region', { name: 'Swing view (days to weeks)' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Positional view (months)' })).toBeInTheDocument()
    expect(screen.getByText(/Weekly close below ₹740.00/)).toBeInTheDocument()
  })

  it('warns about results and lists recent news', async () => {
    setup()
    await analyse('HDFCBANK')

    expect(await screen.findByText(/Results on 17 Nov/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'RBI cuts repo rate unexpectedly' })).toBeInTheDocument()
  })

  it('says whether to keep a stock you already own', async () => {
    const owned = { ...stockAnalysis, holding: holdingsView.latestCheck!.checks[0] } // EXIT, -4.54%
    const { onAnalyze } = setup({ onAnalyze: vi.fn().mockResolvedValue(owned) })

    await userEvent.type(screen.getByLabelText('Stock symbol'), 'AXISBANK')
    await userEvent.click(screen.getByLabelText('I already own this stock'))
    await userEvent.type(screen.getByLabelText('Buy price'), '1400')
    await userEvent.type(screen.getByLabelText('Buy date'), '2026-11-03')
    await userEvent.clear(screen.getByLabelText('Quantity'))
    await userEvent.type(screen.getByLabelText('Quantity'), '20')
    await userEvent.type(screen.getByLabelText(/Your stop loss/), '1340')
    await userEvent.click(screen.getByRole('button', { name: 'Analyse' }))

    expect(onAnalyze).toHaveBeenCalledWith('AXISBANK', {
      quantity: 20, buyPrice: 1400, buyDate: '2026-11-03', horizon: 'SWING', stopLoss: 1340,
    })
    const position = await screen.findByRole('region', { name: 'Your position' })
    expect(within(position).getByText('Exit')).toBeInTheDocument()
    expect(within(position).getByText('-4.54%')).toBeInTheDocument()
    expect(within(position).getByText(/Closed below your stop/)).toBeInTheDocument()
  })

  it('explains a stock without enough history for the weekly view', async () => {
    setup({ onAnalyze: vi.fn().mockResolvedValue({ ...stockAnalysis, positional: null,
      positionalUnavailable: 'NEWIPO has only 30 weeks of history - the weekly (positional) view needs about a year' }) })
    await analyse('NEWIPO')

    const positional = await screen.findByRole('region', { name: 'Positional view (months)' })
    expect(within(positional).getByText(/only 30 weeks of history/)).toBeInTheDocument()
  })

  it('shows why a stock could not be analysed', async () => {
    setup({ onAnalyze: vi.fn().mockRejectedValue(new Error('No NSE price data found for NOSUCH - check the symbol')) })
    await analyse('NOSUCH')

    expect(await screen.findByRole('alert')).toHaveTextContent('No NSE price data found for NOSUCH')
  })

  it('asks Claude for a plain-English explanation only when you click', async () => {
    const { onExplain } = setup()
    await analyse('HDFCBANK')
    expect(onExplain).not.toHaveBeenCalled()

    await userEvent.click(await screen.findByRole('button', { name: /explain with ai/i }))

    expect(onExplain).toHaveBeenCalledWith('HDFCBANK', undefined)
    expect(await screen.findByText('HDFC Bank looks strong on both horizons.')).toBeInTheDocument()
  })

  it('suggests Nifty 500 symbols as you type', () => {
    setup()

    expect(screen.getByLabelText('Stock symbol')).toHaveAttribute('list', 'stock-list')
    expect(document.querySelector('#stock-list option[value="HDFCBANK"]')).not.toBeNull()
  })
})
