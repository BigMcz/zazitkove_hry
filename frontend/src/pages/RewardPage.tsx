import { Link, Navigate, useLocation } from 'react-router'
import ErrorBanner from '../components/ErrorBanner.tsx'
import Page from '../components/Page.tsx'
import PuzzleLetters from '../components/PuzzleLetters.tsx'
import StoryText from '../components/StoryText.tsx'
import VoicePlayer from '../components/VoicePlayer.tsx'
import { buttonPrimary } from '../components/ui.ts'
import { usePlaySession } from '../session/usePlaySession.ts'
import type { RewardLocationState } from './StationPage.tsx'

export default function RewardPage() {
  const { query, loadError, texts } = usePlaySession()
  const location = useLocation()
  const fromAnswer = location.state as RewardLocationState | null
  const state = query.data

  if (!state) {
    return (
      <Page texts={texts}>
        {loadError ? (
          <ErrorBanner message={texts.error(loadError)} onRetry={() => void query.refetch()} />
        ) : (
          <p role="status">{texts.loading}</p>
        )}
      </Page>
    )
  }

  // After a reload the navigation state is gone, the last solved station has the same reward.
  const lastSolved = state.solvedStations.at(-1)
  const rewardText = fromAnswer?.rewardText ?? lastSolved?.rewardText
  const rewardAudioUrl = fromAnswer?.rewardText ? fromAnswer.rewardAudioUrl : lastSolved?.rewardAudioUrl
  const nextText = fromAnswer?.rewardText ? fromAnswer.nextText : lastSolved?.nextText
  if (!rewardText) {
    return <Navigate to="/play" replace />
  }

  const completed = state.status === 'COMPLETED' || fromAnswer?.gameCompleted === true

  return (
    <Page texts={texts}>
      <header className="flex flex-col gap-1">
        <p className="text-sm font-semibold tracking-wide text-emerald-800 uppercase">
          {texts.solved(state.progress.solved, state.progress.total)}
        </p>
        <h1 className="font-serif text-3xl font-bold text-stone-950">
          {completed ? texts.lastClue : texts.clueFound}
        </h1>
      </header>

      <div className="rounded-2xl border-2 border-emerald-700 bg-emerald-50 p-5">
        {rewardAudioUrl && (
          <div className="mb-3">
            <VoicePlayer src={rewardAudioUrl} />
          </div>
        )}
        <StoryText text={rewardText} />
      </div>

      {state.puzzle && <PuzzleLetters puzzle={state.puzzle} highlightLatest />}

      {nextText && !completed && (
        <section aria-labelledby="next-heading" className="rounded-xl border-l-4 border-amber-600 bg-white p-4 shadow-sm">
          <h2 id="next-heading" className="text-sm font-semibold tracking-wide text-stone-700 uppercase">
            {texts.whereNext}
          </h2>
          <p className="mt-1 text-lg font-semibold text-stone-950">{nextText}</p>
        </section>
      )}

      <Link to={completed ? '/play/finale' : '/play'} replace className={buttonPrimary}>
        {completed ? texts.toFinale : texts.toNextStation}
      </Link>
    </Page>
  )
}
