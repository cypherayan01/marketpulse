import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { performance } from '../test/fixtures'
import { TrackRecordPanel } from './TrackRecordPanel'

describe('TrackRecordPanel', () => {
  it('shows each style’s win rate and average return per trade', () => {
    render(<TrackRecordPanel performance={performance} />)

    const swing = screen.getByRole('region', { name: 'Swing track record' })
    expect(within(swing).getByLabelText('Win rate')).toHaveTextContent('50%')
    expect(within(swing).getByLabelText('Average return per trade')).toHaveTextContent('+0.50%')
    expect(within(swing).getByText(/avg win \+5.50% · avg loss -4.50%/i)).toBeInTheDocument()
    expect(within(swing).getByText(/1 target · 2 stopped · 1 expired/)).toBeInTheDocument()
  })

  it('compares score bands so you can see whether higher scores do better', () => {
    render(<TrackRecordPanel performance={performance} />)

    const swing = screen.getByRole('region', { name: 'Swing track record' })
    expect(within(swing).getByRole('row', { name: /80-100.*2.*50%.*\+2.50%/ })).toBeInTheDocument()
    expect(within(swing).getByRole('row', { name: /65-79.*2.*50%.*-1.50%/ })).toBeInTheDocument()
  })

  it('warns that a few trades prove nothing yet', () => {
    render(<TrackRecordPanel performance={performance} />)

    expect(screen.getAllByText(/too few closed trades/i).length).toBeGreaterThan(0)
  })

  it('lists open and recently closed paper trades', () => {
    render(<TrackRecordPanel performance={performance} />)

    const open = screen.getByRole('region', { name: 'Open paper trades' })
    expect(within(open).getByText('AXISBANK')).toBeInTheDocument()
    expect(within(open).getByText('₹1,336.40')).toBeInTheDocument()
    const closed = screen.getByRole('region', { name: 'Recently closed' })
    expect(within(closed).getByText('HDFCBANK')).toBeInTheDocument()
    expect(within(closed).getByText('Target hit')).toBeInTheDocument()
    expect(within(closed).getByText('+10.00%')).toBeInTheDocument()
  })

  it('names the BTST style in capitals in the trade lists', () => {
    const btstTrade = { ...performance.open[0], id: 5, style: 'BTST' as const, symbol: 'TRENT' }
    render(<TrackRecordPanel performance={{ ...performance, open: [btstTrade] }} />)

    const open = screen.getByRole('region', { name: 'Open paper trades' })
    expect(within(open).getByText('BTST')).toBeInTheDocument()
    expect(within(open).queryByText('Btst')).not.toBeInTheDocument()
  })

  it('explains an empty style', () => {
    render(<TrackRecordPanel performance={performance} />)

    const btst = screen.getByRole('region', { name: 'BTST track record' })
    expect(within(btst).getByText(/no closed trades yet/i)).toBeInTheDocument()
  })
})
