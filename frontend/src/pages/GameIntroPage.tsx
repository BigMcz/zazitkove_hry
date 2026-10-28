import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client.ts'
import { useGame, useStartSession } from '../api/queries.ts'
import ErrorBanner from '../components/ErrorBanner.tsx'
import Page from '../components/Page.tsx'
import StoryText from '../components/StoryText.tsx'
import { buttonPrimary, input, linkSubtle } from '../components/ui.ts'
import { useSessionToken } from '../session/sessionTokenContext.ts'

export default function GameIntroPage() {
  const { gameId = '' } = useParams()
  const navigate = useNavigate()
  const { token, setToken } = useSessionToken()
  const game = useGame(gameId)
  const startSession = useStartSession()
  const [teamName, setTeamName] = useState('')

  async function handleStart(event: FormEvent) {
    event.preventDefault()
    if (token) {
      // Starting a new game ends the previous one; ignore failures (it may already be gone).
      await api.abandon(token).catch(() => undefined)
    }
    startSession.mutate(
      { gameId, teamName: teamName.trim() || null },
      {
        onSuccess: (state) => {
          if (state.token) {
            setToken(state.token)
          }
          navigate('/play')
        },
      },
    )
  }

  return (
    <Page>
      <Link to="/" className={linkSubtle}>
        ← Zpět na výběr her
      </Link>

      {game.isPending && <p role="status">Načítám hru…</p>}

      {game.isError && <ErrorBanner message={game.error.message} onRetry={() => void game.refetch()} />}

      {game.data && (
        <>
          <header className="flex flex-col gap-1">
            <h1 className="font-serif text-3xl font-bold text-stone-950">{game.data.title}</h1>
            {game.data.subtitle && <p className="text-lg text-stone-700">{game.data.subtitle}</p>}
            <p className="text-sm font-semibold text-stone-600">
              {game.data.stationCount} stanovišť
              {game.data.estimatedMinutes && ` · cca ${game.data.estimatedMinutes} min`}
            </p>
          </header>

          <StoryText text={game.data.introText} />

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
            {startSession.isError && <ErrorBanner message={startSession.error.message} />}
            <button type="submit" disabled={startSession.isPending} className={buttonPrimary}>
              {startSession.isPending ? 'Začínám…' : 'Začít hru'}
            </button>
          </form>
        </>
      )}
    </Page>
  )
}
