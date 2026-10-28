import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import HintList from './HintList.tsx'

const firstHint = { orderNo: 1, text: 'Myslí se něco, co vzniká kopáním.', penaltySeconds: 60 }

describe('HintList', () => {
  it('reveals a hint only after confirming the penalty', async () => {
    const onReveal = vi.fn()
    render(<HintList revealedHints={[]} hintsAvailable={2} nextHintPenaltySeconds={60} onReveal={onReveal} />)

    await userEvent.click(screen.getByRole('button', { name: 'Potřebuju nápovědu' }))
    expect(onReveal).not.toHaveBeenCalled()
    expect(screen.getByText('+1 min')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ano, ukaž nápovědu' }))
    expect(onReveal).toHaveBeenCalledOnce()
  })

  it('can cancel the confirmation', async () => {
    const onReveal = vi.fn()
    render(<HintList revealedHints={[]} hintsAvailable={1} nextHintPenaltySeconds={60} onReveal={onReveal} />)

    await userEvent.click(screen.getByRole('button', { name: 'Potřebuju nápovědu' }))
    await userEvent.click(screen.getByRole('button', { name: 'Zpět' }))

    expect(onReveal).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Potřebuju nápovědu' })).toBeInTheDocument()
  })

  it('lists revealed hints with their penalty and offers the next one', () => {
    render(
      <HintList revealedHints={[firstHint]} hintsAvailable={1} nextHintPenaltySeconds={120} onReveal={vi.fn()} />,
    )

    expect(screen.getByRole('listitem')).toHaveTextContent('Nápověda 1 · +1 min')
    expect(screen.getByRole('listitem')).toHaveTextContent(firstHint.text)
    expect(screen.getByRole('button', { name: 'Další nápověda' })).toBeInTheDocument()
  })

  it('says there is no other hint when all are revealed', () => {
    render(<HintList revealedHints={[firstHint]} hintsAvailable={0} nextHintPenaltySeconds={null} onReveal={vi.fn()} />)

    expect(screen.getByText('Další nápověda už není.')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('renders nothing for a station without hints', () => {
    const { container } = render(
      <HintList revealedHints={[]} hintsAvailable={0} nextHintPenaltySeconds={null} onReveal={vi.fn()} />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('disables the button while a hint is loading', () => {
    render(<HintList revealedHints={[]} hintsAvailable={1} nextHintPenaltySeconds={60} pending onReveal={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Potřebuju nápovědu' })).toBeDisabled()
  })
})
