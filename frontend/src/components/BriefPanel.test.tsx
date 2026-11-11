import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dashboard } from '../test/fixtures'
import { BriefPanel } from './BriefPanel'

describe('BriefPanel', () => {
  it('shows the AI brief and the exact global numbers', () => {
    render(<BriefPanel brief={dashboard.brief} />)

    expect(screen.getByText(/Strong FII inflows support sentiment/)).toBeInTheDocument()
    expect(screen.getByText('S&P 500')).toBeInTheDocument()
    expect(screen.getByText('+1.13%')).toBeInTheDocument()
    expect(screen.getByText('-0.77%')).toBeInTheDocument()
    expect(screen.getByText(/Unavailable: Gold/)).toBeInTheDocument()
  })

  it('explains when there is no brief yet', () => {
    render(<BriefPanel brief={null} />)

    expect(screen.getByText(/no brief yet/i)).toBeInTheDocument()
  })
})
