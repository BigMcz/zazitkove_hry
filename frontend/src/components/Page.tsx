import type { ReactNode } from 'react'
import { type GameTexts, GameTextsContext, useGameTexts } from '../i18n/gameTexts.ts'

/** A screen of the game; `texts` puts it and everything in it into the language of the game. */
export default function Page({ texts, children }: { texts?: GameTexts; children: ReactNode }) {
  const inherited = useGameTexts()
  const current = texts ?? inherited
  return (
    <GameTextsContext value={current}>
      <main lang={current.language} className="mx-auto flex w-full max-w-lg flex-col gap-6 px-4 pt-6 pb-12">
        {children}
      </main>
    </GameTextsContext>
  )
}
