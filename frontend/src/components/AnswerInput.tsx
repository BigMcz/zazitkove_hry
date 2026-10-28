import { useState, type FormEvent } from 'react'
import type { TaskType } from '../api/types.ts'
import { buttonChoice, buttonPrimary, input } from './ui.ts'

interface AnswerInputProps {
  taskType: TaskType
  choices: string[] | null
  disabled?: boolean
  onSubmit: (answer: string) => void
}

/**
 * TEXT: plain text field, NUMBER: text field with numeric keyboard (inputMode, so that answers
 * like "56 schodů" are still allowed), CHOICE: one large button per choice that submits immediately.
 */
export default function AnswerInput({ taskType, choices, disabled = false, onSubmit }: AnswerInputProps) {
  const [value, setValue] = useState('')

  if (taskType === 'CHOICE') {
    return (
      <div role="group" aria-label="Vyber odpověď" className="flex flex-col gap-3">
        {(choices ?? []).map((choice) => (
          <button
            key={choice}
            type="button"
            disabled={disabled}
            onClick={() => onSubmit(choice)}
            className={buttonChoice}
          >
            {choice}
          </button>
        ))}
      </div>
    )
  }

  const answer = value.trim()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (answer !== '' && !disabled) {
      onSubmit(answer)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      <label htmlFor="answer" className="text-base font-semibold text-stone-800">
        Tvoje odpověď
      </label>
      <input
        id="answer"
        name="answer"
        type="text"
        inputMode={taskType === 'NUMBER' ? 'numeric' : 'text'}
        autoComplete="off"
        autoCorrect="off"
        autoCapitalize="off"
        spellCheck={false}
        enterKeyHint="send"
        maxLength={200}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        className={input}
      />
      <button type="submit" disabled={disabled || answer === ''} className={buttonPrimary}>
        Odeslat odpověď
      </button>
    </form>
  )
}
