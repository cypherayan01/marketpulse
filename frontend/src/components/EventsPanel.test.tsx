import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { dashboard } from '../test/fixtures'
import { EventsPanel } from './EventsPanel'

describe('EventsPanel', () => {
  it('lists upcoming events with their impact', () => {
    render(<EventsPanel events={dashboard.events} />)

    expect(screen.getByText('RBI monetary policy decision')).toBeInTheDocument()
    expect(screen.getByText('Fri 04 Dec')).toBeInTheDocument()
    expect(screen.getByText('HIGH')).toBeInTheDocument()
  })
})
