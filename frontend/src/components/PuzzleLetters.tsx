import type { Puzzle } from '../api/types.ts'

interface PuzzleLettersProps {
  puzzle: Puzzle
  /** Highlight the letter just earned (on the reward page). */
  highlightLatest?: boolean
}

/** Letters of the final puzzle collected so far; the server sends them scrambled, empty slots are null. */
export default function PuzzleLetters({ puzzle, highlightLatest = false }: PuzzleLettersProps) {
  const collected = puzzle.slots.filter((letter) => letter !== null).length
  return (
    <div className="flex flex-col gap-1.5">
      <p className="text-sm font-semibold tracking-wide text-stone-700 uppercase">
        Tajenka {collected} / {puzzle.slots.length}
      </p>
      <ol aria-label="Tajenka" className="flex flex-wrap gap-1">
        {puzzle.slots.map((letter, index) => {
          const latest = highlightLatest && index === puzzle.latestSlot
          return (
            <li
              key={index}
              aria-label={letter ?? 'zatím nic'}
              aria-current={latest ? 'true' : undefined}
              className={`flex h-10 w-8 items-center justify-center rounded-md border-2 font-serif text-xl font-bold ${
                letter === null
                  ? 'border-dashed border-stone-300 bg-stone-50'
                  : latest
                    ? 'border-emerald-700 bg-emerald-100 text-emerald-900'
                    : 'border-amber-600 bg-amber-50 text-stone-950'
              }`}
            >
              {letter}
            </li>
          )
        })}
      </ol>
    </div>
  )
}
