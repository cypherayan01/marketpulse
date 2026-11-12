import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { dayPredictions, liveScorecard, replayScorecard } from '../test/fixtures'
import { PredictionsPanel } from './PredictionsPanel'

function setup(overrides: Partial<Parameters<typeof PredictionsPanel>[0]> = {}) {
  const props = {
    initial: liveScorecard,
    dates: ['2026-11-12', '2026-11-11'],
    loadScorecard: vi.fn().mockResolvedValue(replayScorecard),
    loadDates: vi.fn().mockResolvedValue(['2026-11-11']),
    loadDay: vi.fn().mockResolvedValue(dayPredictions),
    onRunReplay: vi.fn(),
    ...overrides,
  }
  render(<PredictionsPanel {...props} />)
  return props
}

describe('PredictionsPanel', () => {
  it('leads with each style’s hit rate at its key checkpoint', () => {
    setup()

    const swing = screen.getByRole('region', { name: 'Swing predictions' })
    expect(within(swing).getByLabelText('Right after 5 sessions')).toHaveTextContent('58%')
    expect(within(swing).getByLabelText('Average edge vs Nifty')).toHaveTextContent('+0.40 pts')
    expect(within(swing).getByText('240 predictions')).toBeInTheDocument()
  })

  it('charts the hit rate at every checkpoint against a coin flip', () => {
    setup()

    const chart = screen.getByRole('figure', { name: /Swing: share of calls right at each checkpoint/ })
    expect(within(chart).getAllByRole('listitem')).toHaveLength(6) // only measured checkpoints
    expect(within(chart).getByText('50% = coin flip')).toBeInTheDocument()
    expect(within(chart).getByLabelText('5 sessions: 58% right, edge +0.40 pts, 40 calls')).toBeInTheDocument()
  })

  it('compares groups so you can see what works', () => {
    setup()

    const swing = screen.getByRole('region', { name: 'Swing predictions' })
    expect(within(swing).getByRole('row', { name: /Buy 80\+.*64%.*\+1.90/ })).toBeInTheDocument()
    expect(within(swing).getByRole('row', { name: /Small cap.*49%.*-0.30/ })).toBeInTheDocument()
  })

  it('shows BTST results the next morning', () => {
    setup()

    const btst = screen.getByRole('region', { name: 'BTST predictions' })
    expect(within(btst).getByLabelText('Opened higher next morning')).toHaveTextContent('62%')
    expect(within(btst).getByLabelText('Average next-day high')).toHaveTextContent('+1.60%')
  })

  it('switches to the history replay and shows why it is only an estimate', async () => {
    const { loadScorecard } = setup()

    await userEvent.click(screen.getByRole('button', { name: /history replay/i }))

    expect(loadScorecard).toHaveBeenCalledWith('REPLAY')
    expect(await screen.findByText(/an estimate/)).toBeInTheDocument()
    expect(screen.getByText(/18,000 predictions · 12 Nov 2025 – 12 Nov 2026/)).toBeInTheDocument()
  })

  it('shows what was predicted on a day and how each call turned out', async () => {
    const { loadDay } = setup()

    await userEvent.selectOptions(screen.getByLabelText('Predictions made on'), '2026-11-11')

    expect(loadDay).toHaveBeenCalledWith('2026-11-11', 'LIVE')
    const row = await screen.findByRole('row', { name: /HDFCBANK/ })
    expect(within(row).getByText('Buy')).toBeInTheDocument()
    expect(within(row).getByText('+2.50')).toBeInTheDocument() // 5-day edge: 3.5 - 1.0
    expect(within(row).getByLabelText('Right after 5 sessions')).toBeInTheDocument()
    const avoid = screen.getByRole('row', { name: /NTPC/ })
    expect(within(avoid).getByText('-0.70')).toBeInTheDocument() // avoid: 0.2 - 0.9
  })

  it('offers the replay when there are no live predictions yet', async () => {
    const empty = { ...liveScorecard, predictions: 0, firstDate: null, lastDate: null,
      styles: liveScorecard.styles.map((s) => ({ ...s, predictions: 0, checkpoints: s.checkpoints.map((c) => ({ ...c, measured: 0 })) })) }
    const { onRunReplay } = setup({ initial: empty, dates: [] })

    expect(screen.getByText(/no live predictions yet/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /run the history replay/i }))
    expect(onRunReplay).toHaveBeenCalled()
  })
})
