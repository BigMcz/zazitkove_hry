import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import AnswerInput from './AnswerInput.tsx'

describe('AnswerInput', () => {
  it('submits trimmed text answer with the button', async () => {
    const onSubmit = vi.fn()
    render(<AnswerInput taskType="TEXT" choices={null} onSubmit={onSubmit} />)

    await userEvent.type(screen.getByLabelText('Tvoje odpověď'), '  Jáma  ')
    await userEvent.click(screen.getByRole('button', { name: 'Odeslat odpověď' }))

    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('Jáma')
  })

  it('submits with Enter', async () => {
    const onSubmit = vi.fn()
    render(<AnswerInput taskType="TEXT" choices={null} onSubmit={onSubmit} />)

    await userEvent.type(screen.getByLabelText('Tvoje odpověď'), 'brána{Enter}')

    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('brána')
  })

  it('does not submit an empty answer', async () => {
    const onSubmit = vi.fn()
    render(<AnswerInput taskType="TEXT" choices={null} onSubmit={onSubmit} />)

    await userEvent.type(screen.getByLabelText('Tvoje odpověď'), '   {Enter}')

    expect(screen.getByRole('button', { name: 'Odeslat odpověď' })).toBeDisabled()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows numeric keyboard for NUMBER but still allows any text', async () => {
    const onSubmit = vi.fn()
    render(<AnswerInput taskType="NUMBER" choices={null} onSubmit={onSubmit} />)
    const field = screen.getByLabelText('Tvoje odpověď')

    expect(field).toHaveAttribute('inputmode', 'numeric')
    await userEvent.type(field, '56 schodů{Enter}')
    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('56 schodů')
  })

  it('renders CHOICE as large buttons that submit the chosen text', async () => {
    const onSubmit = vi.fn()
    render(<AnswerInput taskType="CHOICE" choices={['Meč', 'Provaz', 'Lucernu', 'Klíč']} onSubmit={onSubmit} />)

    expect(screen.queryByLabelText('Tvoje odpověď')).not.toBeInTheDocument()
    expect(screen.getAllByRole('button').map((button) => button.textContent)).toEqual([
      'Meč',
      'Provaz',
      'Lucernu',
      'Klíč',
    ])

    await userEvent.click(screen.getByRole('button', { name: 'Lucernu' }))
    expect(onSubmit).toHaveBeenCalledExactlyOnceWith('Lucernu')
  })

  it('ignores clicks while disabled', async () => {
    const onSubmit = vi.fn()
    render(<AnswerInput taskType="CHOICE" choices={['Meč']} disabled onSubmit={onSubmit} />)

    await userEvent.click(screen.getByRole('button', { name: 'Meč' }))

    expect(onSubmit).not.toHaveBeenCalled()
  })
})
