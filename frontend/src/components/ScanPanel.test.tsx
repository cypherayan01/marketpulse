import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { swingReport } from '../test/fixtures'
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

  it('says so when there are no setups', () => {
    const report = { ...swingReport, bullish: [], bearish: [] }
    render(<ScanPanel title="Swing" job="eod" report={report} onRun={vi.fn()} />)

    expect(screen.getByText(/no setups/i)).toBeInTheDocument()
  })
})
