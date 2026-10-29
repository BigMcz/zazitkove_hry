import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { api } from '../api/client.ts'
import { useStartSession } from '../api/queries.ts'
import type { CodeCheck } from '../api/types.ts'
import ErrorBanner from '../components/ErrorBanner.tsx'
import Page from '../components/Page.tsx'
import StoryImage from '../components/StoryImage.tsx'
import StoryText from '../components/StoryText.tsx'
import VoicePlayer from '../components/VoicePlayer.tsx'
import { buttonPrimary, input, linkSubtle } from '../components/ui.ts'
import { useSessionToken } from '../session/sessionTokenContext.ts'

export interface StartLocationState {
  check: CodeCheck
}

/** Intro of the game the code belongs to; starting the game activates the code. */
export default function StartPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { token, setToken } = useSessionToken()
  const startSession = useStartSession()
  const [teamName, setTeamName] = useState('')
  const check = (location.state as StartLocationState | null)?.check

  // The code is kept only in the navigation state (not in the URL); after a reload start again.
  if (!check) {
    return <Navigate to="/" replace />
  }
  const { game } = check

  async function handleStart(event: FormEvent) {
    event.preventDefault()
    if (token) {
      // Starting a new game ends the previous one; ignore failures (it may already be gone).
      await api.abandon(token).catch(() => undefined)
    }
    startSession.mutate(
      { code: check!.code, teamName: teamName.trim() || null },
      {
        onSuccess: (state) => {
          if (state.token) {
            setToken(state.token)
          }
          navigate('/play', { replace: true })
        },
      },
    )
  }

  return (
    <Page>
      <Link to="/" className={linkSubtle}>
        ← Zpět
      </Link>

      <header className="flex flex-col gap-1">
        <h1 className="font-serif text-3xl font-bold text-stone-950">{game.title}</h1>
        {game.subtitle && <p className="text-lg text-stone-700">{game.subtitle}</p>}
        <p className="text-sm font-semibold text-stone-600">
          {game.stationCount} stanovišť
          {game.estimatedMinutes && ` · cca ${game.estimatedMinutes} min`}
        </p>
      </header>

      {game.coverImageUrl && <StoryImage src={game.coverImageUrl} alt="" />}
      {game.introAudioUrl && <VoicePlayer src={game.introAudioUrl} />}
      <StoryText text={game.introText} />

      <form onSubmit={(event) => void handleStart(event)} className="flex flex-col gap-3">
        <label htmlFor="team-name" className="text-base font-semibold text-stone-800">
          Název týmu <span className="font-normal text-stone-600">(nepovinné)</span>
        </label>
        <input
          id="team-name"
          type="text"
          autoComplete="off"
          maxLength={100}
          value={teamName}
          onChange={(event) => setTeamName(event.target.value)}
          className={input}
        />
        {token && <p className="text-base text-stone-700">Začátkem nové hry ukončíš tu rozehranou.</p>}
        <p className="text-sm text-stone-600">
          Po spuštění hry začne běžet platnost kódu <span className="font-mono whitespace-nowrap">{check.code}</span>.
        </p>
        {startSession.isError && <ErrorBanner message={startSession.error.message} />}
        <button type="submit" disabled={startSession.isPending} className={buttonPrimary}>
          {startSession.isPending ? 'Začínám…' : 'Začít hru'}
        </button>
      </form>
    </Page>
  )
}
