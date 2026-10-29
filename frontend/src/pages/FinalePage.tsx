import { Navigate } from 'react-router'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { formatDuration } from '../components/formatDuration.ts'
import Page from '../components/Page.tsx'
import { praiseForHints } from '../components/praiseForHints.ts'
import StoryText from '../components/StoryText.tsx'
import VoicePlayer from '../components/VoicePlayer.tsx'
import { buttonPrimary, card } from '../components/ui.ts'
import { usePlaySession } from '../session/usePlaySession.ts'

export default function FinalePage() {
  const { query, loadError, leaveGame } = usePlaySession()
  const state = query.data

  if (!state) {
    return (
      <Page>
        {loadError ? (
          <ErrorBanner message={loadError.message} onRetry={() => void query.refetch()} />
        ) : (
          <p role="status">Načítám…</p>
        )}
      </Page>
    )
  }

  if (state.status !== 'COMPLETED' || !state.finale) {
    return <Navigate to="/play" replace />
  }

  const { finale } = state

  return (
    <Page>
      <header className="flex flex-col gap-1">
        <p className="text-sm font-semibold tracking-wide text-emerald-800 uppercase">Hra dokončena</p>
        <h1 className="font-serif text-3xl font-bold text-stone-950">{state.game.title}</h1>
      </header>

      <div className="rounded-2xl border-2 border-emerald-700 bg-emerald-50 p-5">
        <p className="font-serif text-2xl font-bold text-emerald-900">Gratulujeme!</p>
        <p className="mt-1 text-lg text-stone-900">
          Vyřešili jste všech {state.progress.total} úkolů. {praiseForHints(finale.hintsUsed)}
        </p>
      </div>

      {finale.outroAudioUrl && <VoicePlayer src={finale.outroAudioUrl} />}
      <StoryText text={finale.outroText} />

      <section aria-labelledby="summary-heading" className={card}>
        <h2 id="summary-heading" className="mb-3 text-xl font-bold">
          {state.teamName ? `Výsledek týmu ${state.teamName}` : 'Tvůj výsledek'}
        </h2>
        <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-2 text-lg">
          <dt className="text-stone-700">Celkový čas</dt>
          <dd className="text-right font-bold">{formatDuration(finale.totalSeconds)}</dd>
          <dt className="text-stone-700">Z toho penalizace</dt>
          <dd className="text-right font-semibold">{formatDuration(state.penaltySeconds)}</dd>
          <dt className="text-stone-700">Použité nápovědy</dt>
          <dd className="text-right font-semibold">{finale.hintsUsed}</dd>
        </dl>
      </section>

      <button type="button" onClick={() => leaveGame()} className={buttonPrimary}>
        Zpět na začátek
      </button>
    </Page>
  )
}
