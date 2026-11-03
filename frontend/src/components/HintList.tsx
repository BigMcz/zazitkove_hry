import { useState } from 'react'
import type { RevealedHint } from '../api/types.ts'
import { useGameTexts } from '../i18n/gameTexts.ts'
import { formatDuration } from './formatDuration.ts'
import { buttonPrimary, buttonSecondary } from './ui.ts'

interface HintListProps {
  revealedHints: RevealedHint[]
  hintsAvailable: number
  nextHintPenaltySeconds: number | null
  pending?: boolean
  /** A game for children: hints say nothing about time and need no confirmation. */
  withoutPenalty?: boolean
  onReveal: () => void
}

/** Revealed hints and a button for the next one. A hint costs time, so it has to be confirmed. */
export default function HintList({
  revealedHints,
  hintsAvailable,
  nextHintPenaltySeconds,
  pending = false,
  withoutPenalty = false,
  onReveal,
}: HintListProps) {
  const [confirming, setConfirming] = useState(false)
  const texts = useGameTexts()

  if (revealedHints.length === 0 && hintsAvailable === 0) {
    return null
  }

  return (
    <section aria-labelledby="hints-heading" className="flex flex-col gap-3">
      <h2 id="hints-heading" className="text-lg font-bold text-stone-900">
        {texts.hints}
      </h2>

      {revealedHints.length > 0 && (
        <ol className="flex flex-col gap-2">
          {revealedHints.map((hint) => (
            <li key={hint.orderNo} className="rounded-xl bg-amber-50 p-4 text-base text-stone-900">
              <span className="block text-sm font-semibold text-stone-700">
                {texts.hint(hint.orderNo)}
                {!withoutPenalty && ` · +${formatDuration(hint.penaltySeconds)}`}
              </span>
              {hint.text}
            </li>
          ))}
        </ol>
      )}

      {hintsAvailable === 0 && <p className="text-base text-stone-700">{texts.noMoreHints}</p>}

      {hintsAvailable > 0 && !confirming && (
        <button
          type="button"
          disabled={pending}
          onClick={() => (withoutPenalty ? onReveal() : setConfirming(true))}
          className={buttonSecondary}
        >
          {revealedHints.length === 0 ? texts.needHint : texts.nextHint}
        </button>
      )}

      {hintsAvailable > 0 && confirming && (
        <div className="flex flex-col gap-3 rounded-xl border-2 border-stone-900 bg-white p-4">
          <p className="text-base text-stone-900">
            {texts.hintCosts}{' '}
            <strong>+{formatDuration(nextHintPenaltySeconds ?? 0)}</strong>.
          </p>
          <button
            type="button"
            disabled={pending}
            onClick={() => {
              setConfirming(false)
              onReveal()
            }}
            className={buttonPrimary}
          >
            {texts.showHint}
          </button>
          <button type="button" onClick={() => setConfirming(false)} className={buttonSecondary}>
            {texts.cancel}
          </button>
        </div>
      )}
    </section>
  )
}
