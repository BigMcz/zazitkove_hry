import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { api } from '../api/client.ts'
import { useRevealHint, useSubmitAnswer } from '../api/queries.ts'
import type { CurrentStation, SessionState } from '../api/types.ts'
import AnswerInput from '../components/AnswerInput.tsx'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { formatDuration } from '../components/formatDuration.ts'
import HintList from '../components/HintList.tsx'
import OfflineStatus from '../components/OfflineStatus.tsx'
import Page from '../components/Page.tsx'
import ProgressBar from '../components/ProgressBar.tsx'
import PuzzleLetters from '../components/PuzzleLetters.tsx'
import StoryImage from '../components/StoryImage.tsx'
import StoryText from '../components/StoryText.tsx'
import VoicePlayer from '../components/VoicePlayer.tsx'
import { playOnce } from '../components/playOnce.ts'
import { buttonSecondary, linkSubtle } from '../components/ui.ts'
import { type GameTexts, useGameTexts } from '../i18n/gameTexts.ts'
import { usePlaySession } from '../session/usePlaySession.ts'

export interface RewardLocationState {
  rewardText?: string
  rewardAudioUrl?: string
  nextText?: string
  gameCompleted?: boolean
}

export default function StationPage() {
  const { token, query, loadError, leaveGame, handleError, texts } = usePlaySession()
  const state = query.data

  if (!state) {
    return (
      <Page texts={texts}>
        {loadError ? (
          <ErrorBanner message={texts.error(loadError)} onRetry={() => void query.refetch()} />
        ) : (
          <p role="status">{texts.loadingGame}</p>
        )}
      </Page>
    )
  }

  if (state.status === 'COMPLETED') {
    return <Navigate to="/play/finale" replace />
  }

  if (state.status !== 'IN_PROGRESS' || !state.currentStation) {
    return <GameEnded texts={texts} onLeave={() => leaveGame()} />
  }

  return (
    <StationView
      // Remount per station so that the answer field, messages and hint confirmation start empty.
      key={state.currentStation.id}
      token={token}
      state={state}
      station={state.currentStation}
      texts={texts}
      onLeave={leaveGame}
      onError={handleError}
    />
  )
}

interface StationViewProps {
  token: string | null
  state: SessionState
  station: CurrentStation
  texts: GameTexts
  onLeave: (message?: string) => void
  onError: (error: Error) => string | null
}

function StationView({ token, state, station, texts, onLeave, onError }: StationViewProps) {
  const navigate = useNavigate()
  const submitAnswer = useSubmitAnswer(token)
  const revealHint = useRevealHint(token)
  const [feedback, setFeedback] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [confirmLeave, setConfirmLeave] = useState(false)

  function handleAnswer(answer: string) {
    setError(null)
    setFeedback(null)
    submitAnswer.mutate(
      { stationId: station.id, answer },
      {
        onSuccess: (result) => {
          if (result.correct) {
            const rewardState: RewardLocationState = {
              rewardText: result.rewardText,
              rewardAudioUrl: result.rewardAudioUrl,
              nextText: result.nextText,
              gameCompleted: result.gameCompleted,
            }
            navigate('/play/reward', { state: rewardState })
          } else {
            setFeedback(result.message ?? texts.wrongAnswer)
            if (result.messageAudioUrl) {
              playOnce(result.messageAudioUrl)
            }
          }
        },
        onError: (err) => setError(onError(err)),
      },
    )
  }

  function handleHint() {
    setError(null)
    revealHint.mutate(station.id, { onError: (err) => setError(onError(err)) })
  }

  async function handleLeave() {
    if (token) {
      await api.abandon(token).catch(() => undefined)
    }
    onLeave(texts.gameLeft)
  }

  return (
    <Page texts={texts}>
      <header className="flex flex-col gap-3">
        <p className="text-sm font-semibold text-stone-600">{state.game.title}</p>
        <ProgressBar current={station.orderNo} total={state.progress.total} />
        <OfflineStatus token={token} />
        {state.puzzle && <PuzzleLetters puzzle={state.puzzle} />}
        {state.penaltySeconds > 0 && (
          <p className="text-sm text-stone-700">{texts.hintPenalty(formatDuration(state.penaltySeconds))}</p>
        )}
      </header>

      <h1 className="font-serif text-3xl font-bold text-stone-950">{station.title}</h1>

      {station.imageUrl && <StoryImage src={station.imageUrl} alt={station.imageAlt ?? ''} />}
      <Story station={station} />

      <section aria-labelledby="task-heading" className="rounded-xl border-l-4 border-amber-600 bg-white p-4 shadow-sm">
        <h2 id="task-heading" className="text-sm font-semibold tracking-wide text-stone-700 uppercase">
          {texts.task}
        </h2>
        <p className="mt-1 text-lg font-semibold text-stone-950">{station.taskText}</p>
      </section>

      <AnswerInput
        taskType={station.taskType}
        choices={station.choices}
        disabled={submitAnswer.isPending}
        onSubmit={handleAnswer}
      />

      {feedback && (
        <p role="alert" className="rounded-xl border-l-4 border-red-700 bg-red-50 p-4 text-lg font-semibold text-red-900">
          {feedback}
        </p>
      )}
      {error && <ErrorBanner message={error} />}

      <HintList
        revealedHints={station.revealedHints}
        hintsAvailable={station.hintsAvailable}
        nextHintPenaltySeconds={station.nextHintPenaltySeconds}
        pending={revealHint.isPending}
        withoutPenalty={state.game.forChildren}
        onReveal={handleHint}
      />

      <footer className="mt-6 border-t border-stone-300 pt-4">
        {!confirmLeave ? (
          <button type="button" onClick={() => setConfirmLeave(true)} className={linkSubtle}>
            {texts.leaveGame}
          </button>
        ) : (
          <div className="flex flex-col gap-3">
            <p className="text-base">{texts.leaveConfirm}</p>
            <button type="button" onClick={() => void handleLeave()} className={buttonSecondary}>
              {texts.leaveYes}
            </button>
            <button type="button" onClick={() => setConfirmLeave(false)} className={linkSubtle}>
              {texts.leaveNo}
            </button>
          </div>
        )}
      </footer>
    </Page>
  )
}

/**
 * Story of the station with its recording. A listening task hides the text behind „Zobrazit přepis“ —
 * unless the recording is missing, then the text is the only way to solve it.
 */
function Story({ station }: { station: CurrentStation }) {
  const [audioMissing, setAudioMissing] = useState(false)
  const [transcriptShown, setTranscriptShown] = useState(false)
  const texts = useGameTexts()
  const hasAudio = station.storyAudioUrl !== null && !audioMissing
  const textHidden = station.listenOnly && hasAudio && !transcriptShown

  return (
    <>
      {station.storyAudioUrl && (
        <VoicePlayer src={station.storyAudioUrl} onUnavailable={() => setAudioMissing(true)} />
      )}
      {textHidden ? (
        <div className="flex flex-col gap-2 rounded-xl bg-stone-100 p-4">
          <p className="text-base text-stone-800">{texts.listenTask}</p>
          <button type="button" onClick={() => setTranscriptShown(true)} className={linkSubtle}>
            {texts.showTranscript}
          </button>
        </div>
      ) : (
        <StoryText text={station.storyText} />
      )}
    </>
  )
}

function GameEnded({ texts, onLeave }: { texts: GameTexts; onLeave: () => void }) {
  return (
    <Page texts={texts}>
      <ErrorBanner tone="info" message={texts.gameNotRunning} />
      <button type="button" onClick={onLeave} className={buttonSecondary}>
        {texts.backToStart}
      </button>
    </Page>
  )
}
