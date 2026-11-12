import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { segmentedReport, swingReport } from '../test/fixtures'
import { ScanPanel } from './ScanPanel'

describe('ScanPanel', () => {
  it('explains when a scan has not run yet and offers to run it', async () => {
    const onRun = vi.fn()
    render(<ScanPanel title="Swing" job="eod" report={null} onRun={onRun} />)

    expect(screen.getByText(/hasn't run since the server started/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /run swing scan now/i }))
    expect(onRun).toHaveBeenCalledWith('eod')
  })

  it('shows the market line, setups and stocks to avoid', () => {
    render(<ScanPanel title="Swing" job="eod" report={swingReport} onRun={vi.fn()} />)

    expect(screen.getByText(/supportive for longs/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'HDFCBANK' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'NTPC' })).toBeInTheDocument()
    expect(screen.getByText(/Results on 17 Nov/)).toBeInTheDocument()
    expect(screen.getByText(/50 stocks scanned/)).toBeInTheDocument()
  })

  it('shows warnings such as overnight event risk', () => {
    const report = { ...swingReport, notes: ['Overnight event risk: US Fed rate decision'] }
    render(<ScanPanel title="BTST" job="btst" report={report} onRun={vi.fn()} />)

    expect(screen.getByText('Overnight event risk: US Fed rate decision')).toBeInTheDocument()
  })

  it('shows one section per market-cap segment with its index move', () => {
    render(<ScanPanel title="Swing" job="eod" report={segmentedReport} onRun={vi.fn()} />)

    const large = screen.getByRole('region', { name: 'Large cap' })
    expect(within(large).getByText('Nifty 100 +0.62% today · 100 scanned')).toBeInTheDocument()
    expect(within(large).getByRole('heading', { name: 'HDFCBANK' })).toBeInTheDocument()
    expect(within(large).getByRole('heading', { name: 'NTPC' })).toBeInTheDocument()
    const small = screen.getByRole('region', { name: 'Small cap' })
    expect(within(small).getByText('Nifty Smallcap 100 · 98 scanned')).toBeInTheDocument()
    expect(within(small).getByRole('heading', { name: 'AARTIIND' })).toBeInTheDocument()
  })

  it('filters to one market-cap segment', async () => {
    render(<ScanPanel title="Swing" job="eod" report={segmentedReport} onRun={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Small cap' }))

    expect(screen.getByRole('button', { name: 'Small cap' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByRole('region', { name: 'Large cap' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'AARTIIND' })).toBeInTheDocument()
  })

  it('hides setups outside the chosen price range at once', () => {
    render(<ScanPanel title="BTST" job="btst" report={segmentedReport} onRun={vi.fn()}
                      priceRange={{ minPrice: null, maxPrice: 500, label: 'up to ₹500' }} />)

    expect(screen.getByRole('heading', { name: 'AARTIIND' })).toBeInTheDocument() // ₹412.50
    expect(screen.queryByRole('heading', { name: 'HDFCBANK' })).not.toBeInTheDocument() // ₹806.60
    expect(screen.getByText('Showing 1 of 2 setups priced up to ₹500')).toBeInTheDocument()
  })

  it('only offers filters for segments the report has', () => {
    render(<ScanPanel title="Swing" job="eod" report={segmentedReport} onRun={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'All caps' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByRole('button', { name: 'Mid cap' })).not.toBeInTheDocument()
  })

  it('says so when there are no setups', () => {
    const report = { ...swingReport, bullish: [], bearish: [] }
    render(<ScanPanel title="Swing" job="eod" report={report} onRun={vi.fn()} />)

    expect(screen.getByText(/no setups/i)).toBeInTheDocument()
  })
})
