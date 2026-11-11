import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { bearishSignal, bullishSignal } from '../test/fixtures'
import { SignalCard } from './SignalCard'

describe('SignalCard', () => {
  it('shows the score, classification and confidence', () => {
    render(<SignalCard signal={bullishSignal} />)

    expect(screen.getByRole('heading', { name: 'HDFCBANK' })).toBeInTheDocument()
    expect(screen.getByText('86')).toBeInTheDocument()
    expect(screen.getByText('Strong bullish')).toBeInTheDocument()
    expect(screen.getByText(/confidence high/i)).toBeInTheDocument()
  })

  it('shows the trade plan in rupees', () => {
    render(<SignalCard signal={bullishSignal} />)

    expect(screen.getByText('₹806.60')).toBeInTheDocument()
    expect(screen.getByText('₹771.47')).toBeInTheDocument()
    expect(screen.getByText('₹876.86')).toBeInTheDocument()
    expect(screen.getByText('-4.4%')).toBeInTheDocument()
  })

  it('separates reasons from warnings and shows when the idea is wrong', () => {
    render(<SignalCard signal={bullishSignal} />)

    expect(screen.getByText('Above 200-day EMA (long-term uptrend)')).toBeInTheDocument()
    expect(screen.getByText(/RSI 77 overbought/)).toBeInTheDocument()
    expect(screen.getByText(/Daily close back below ₹801.90/)).toBeInTheDocument()
  })

  it('warns when results are due during the hold', () => {
    render(<SignalCard signal={bullishSignal} resultsDate="2026-11-17" />)

    expect(screen.getByText(/Results on 17 Nov/)).toBeInTheDocument()
  })

  it('has no trade plan for a stock to avoid', () => {
    render(<SignalCard signal={bearishSignal} />)

    expect(screen.queryByText('Stop')).not.toBeInTheDocument()
    expect(screen.getByText('Below 200-day EMA (long-term downtrend)')).toBeInTheDocument()
  })
})
