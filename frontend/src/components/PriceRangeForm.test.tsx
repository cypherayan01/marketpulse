import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ValidationError } from '../api'
import { PriceRangeForm } from './PriceRangeForm'

describe('PriceRangeForm', () => {
  it('shows the saved range in the boxes', () => {
    render(<PriceRangeForm range={{ minPrice: 100, maxPrice: 800, label: '₹100 - ₹800' }} onSave={vi.fn()} onRunScan={vi.fn()} />)

    expect(screen.getByLabelText('Minimum price (₹)')).toHaveValue(100)
    expect(screen.getByLabelText('Maximum price (₹)')).toHaveValue(800)
    expect(screen.getByText(/₹100 - ₹800/)).toBeInTheDocument()
  })

  it('saves a new range and offers to rescan with it', async () => {
    const onSave = vi.fn().mockResolvedValue({ minPrice: null, maxPrice: 1500, label: 'up to ₹1,500' })
    const onRunScan = vi.fn()
    render(<PriceRangeForm range={null} onSave={onSave} onRunScan={onRunScan} />)

    await userEvent.type(screen.getByLabelText('Maximum price (₹)'), '1500')
    await userEvent.click(screen.getByRole('button', { name: 'Save range' }))

    expect(onSave).toHaveBeenCalledWith(null, 1500)
    expect(await screen.findByRole('status')).toHaveTextContent('Saved: up to ₹1,500')
    await userEvent.click(screen.getByRole('button', { name: 'Run BTST scan now with this range' }))
    expect(onRunScan).toHaveBeenCalled()
  })

  it('clears the range back to any price', async () => {
    const onSave = vi.fn().mockResolvedValue({ minPrice: null, maxPrice: null, label: 'any price' })
    render(<PriceRangeForm range={{ minPrice: 100, maxPrice: 800, label: '₹100 - ₹800' }} onSave={onSave} onRunScan={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }))

    expect(onSave).toHaveBeenCalledWith(null, null)
    expect(screen.getByLabelText('Minimum price (₹)')).toHaveValue(null)
  })

  it('shows why the server rejected the range', async () => {
    const onSave = vi.fn().mockRejectedValue(new ValidationError('Minimum price must not be above the maximum price'))
    render(<PriceRangeForm range={null} onSave={onSave} onRunScan={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Minimum price (₹)'), '900')
    await userEvent.type(screen.getByLabelText('Maximum price (₹)'), '100')
    await userEvent.click(screen.getByRole('button', { name: 'Save range' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Minimum price must not be above')
  })
})
