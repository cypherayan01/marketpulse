import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { dashboard } from '../test/fixtures'
import { NewsPanel } from './NewsPanel'

describe('NewsPanel', () => {
  it('lists scored headlines with their impact', () => {
    render(<NewsPanel news={dashboard.news} />)

    expect(screen.getByRole('link', { name: 'RBI cuts repo rate unexpectedly' })).toHaveAttribute('href', 'https://news/1')
    expect(screen.getByText('9/10')).toBeInTheDocument()
    expect(screen.getByText('Cheaper credit.')).toBeInTheDocument()
    expect(screen.getByText('Crude oil inches up 0.2%')).toBeInTheDocument()
  })

  it('can show only the headlines that triggered an alert', async () => {
    render(<NewsPanel news={dashboard.news} />)

    await userEvent.click(screen.getByRole('checkbox', { name: /alerts only/i }))

    expect(screen.getByText('RBI cuts repo rate unexpectedly')).toBeInTheDocument()
    expect(screen.queryByText('Crude oil inches up 0.2%')).not.toBeInTheDocument()
  })
})
