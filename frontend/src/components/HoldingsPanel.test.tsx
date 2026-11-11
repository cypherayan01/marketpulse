import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { holdingsView } from '../test/fixtures'
import { HoldingsPanel } from './HoldingsPanel'

function setup(overrides: Partial<Parameters<typeof HoldingsPanel>[0]> = {}) {
  const props = {
    view: holdingsView,
    onAdd: vi.fn().mockResolvedValue(undefined),
    onDelete: vi.fn().mockResolvedValue(undefined),
    onCheck: vi.fn(),
    ...overrides,
  }
  render(<HoldingsPanel {...props} />)
  return props
}

describe('HoldingsPanel', () => {
  it('shows each holding’s verdict, P&L and stop, exits first', () => {
    setup()

    const cards = screen.getAllByRole('article')
    expect(within(cards[0]).getByRole('heading', { name: 'AXISBANK' })).toBeInTheDocument()
    expect(within(cards[0]).getByText('EXIT')).toBeInTheDocument()
    expect(within(cards[0]).getByText('-4.54%')).toBeInTheDocument()
    expect(within(cards[0]).getByText(/Closed below your stop/)).toBeInTheDocument()
    expect(within(cards[1]).getByText('HOLD')).toBeInTheDocument()
    expect(within(cards[1]).getByText(/trailing stop/)).toBeInTheDocument()
  })

  it('says where the stop comes from in plain words', () => {
    setup()

    expect(screen.getByText('Stop: your stop')).toBeInTheDocument()
    expect(screen.getByText('Stop: trailing stop, 3 ATR below the highest close since you bought')).toBeInTheDocument()
  })

  it('colours a review differently from an exit or a hold', () => {
    const review = {
      ...holdingsView.latestCheck!.checks[1],
      holding: holdingsView.holdings[1], verdict: 'REVIEW' as const,
    }
    setup({ view: { ...holdingsView, latestCheck: { ...holdingsView.latestCheck!, checks: [review] } } })

    expect(screen.getByRole('article', { name: 'INFY' })).toHaveClass('review')
  })

  it('shows holdings that have not been checked yet', () => {
    setup()

    const infy = screen.getByRole('article', { name: 'INFY' })
    expect(within(infy).getByText(/not checked yet/i)).toBeInTheDocument()
  })

  it('shows total profit and loss across checked holdings', () => {
    setup()

    expect(screen.getByLabelText('Total P&L')).toHaveTextContent('-₹272.00')
  })

  it('leaves holdings you have removed out of the total', () => {
    // the last check still contains AXISBANK, but it has been removed since
    setup({ view: { ...holdingsView, holdings: holdingsView.holdings.filter((h) => h.symbol !== 'AXISBANK') } })

    expect(screen.getByLabelText('Total P&L')).toHaveTextContent('+₹1,000.00')
  })

  it('says so when removing a holding fails', async () => {
    setup({ onDelete: vi.fn().mockRejectedValue(new Error('Server unavailable')) })

    await userEvent.click(screen.getByRole('button', { name: 'Remove TCS' }))

    expect(await screen.findByRole('alert')).toHaveTextContent("Couldn't remove TCS: Server unavailable")
  })

  it('adds a holding from the form', async () => {
    const { onAdd } = setup()

    await userEvent.type(screen.getByLabelText('Symbol'), 'hdfcbank')
    await userEvent.type(screen.getByLabelText('Quantity'), '15')
    await userEvent.type(screen.getByLabelText('Buy price'), '806.6')
    await userEvent.type(screen.getByLabelText('Buy date'), '2026-11-11')
    await userEvent.selectOptions(screen.getByLabelText('Horizon'), 'POSITIONAL')
    await userEvent.type(screen.getByLabelText(/Stop loss/), '770')
    await userEvent.click(screen.getByRole('button', { name: 'Add holding' }))

    expect(onAdd).toHaveBeenCalledWith({
      symbol: 'HDFCBANK', quantity: 15, buyPrice: 806.6, buyDate: '2026-11-11', horizon: 'POSITIONAL',
      stopLoss: 770, target: null,
    })
    expect(screen.getByLabelText('Symbol')).toHaveValue('')
  })

  it('shows the server’s reason when a holding is rejected', async () => {
    setup({ onAdd: vi.fn().mockRejectedValue(new Error('No NSE price data found for NOSUCH - check the symbol')) })

    await userEvent.type(screen.getByLabelText('Symbol'), 'NOSUCH')
    await userEvent.type(screen.getByLabelText('Quantity'), '1')
    await userEvent.type(screen.getByLabelText('Buy price'), '10')
    await userEvent.type(screen.getByLabelText('Buy date'), '2026-11-11')
    await userEvent.click(screen.getByRole('button', { name: 'Add holding' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No NSE price data found for NOSUCH')
    expect(screen.getByLabelText('Symbol')).toHaveValue('NOSUCH') // kept so it can be corrected
  })

  it('removes a holding', async () => {
    const { onDelete } = setup()

    await userEvent.click(screen.getByRole('button', { name: 'Remove TCS' }))

    expect(onDelete).toHaveBeenCalledWith(1)
  })

  it('can run the check on demand', async () => {
    const { onCheck } = setup()

    await userEvent.click(screen.getByRole('button', { name: /check holdings now/i }))

    expect(onCheck).toHaveBeenCalled()
  })

  it('invites you to add holdings when there are none', () => {
    setup({ view: { holdings: [], latestCheck: null } })

    expect(screen.getByText(/add the stocks you own/i)).toBeInTheDocument()
  })
})
