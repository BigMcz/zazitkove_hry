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
import { voicePreference } from '../components/audioPreference.ts'
import { buttonPrimary, buttonSecondary, card, input, linkSubtle } from '../components/ui.ts'
import { downloadGame } from '../offline/gameDownload.ts'
import { useSessionToken } from '../session/sessionTokenContext.ts'

export interface StartLocationState {
  check: CodeCheck
}

/**
 * Intro of the game the code belongs to; starting the game activates the code. Before the intro the player
 * reads about the sound and safety: the spoken intro would otherwise start on a muted phone unnoticed, and the
 * tap on "Pustit úvod" lets the browser play it.
 */
export default function StartPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { token, setToken } = useSessionToken()
  const startSession = useStartSession()
  const [teamName, setTeamName] = useState('')
  const [prepared, setPrepared] = useState(false)
  const [download, setDownload] = useState<{ done: number; total: number } | null>(null)
  const check = (location.state as StartLocationState | null)?.check

  // The code is kept only in the navigation state (not in the URL); after a reload start again.
  if (!check) {
    return <Navigate to="/" replace />
  }
  const { game } = check

  if (!prepared) {
    return (
      <Page>
        <Link to="/" className={linkSubtle}>
          ← Zpět
        </Link>
        <header className="flex flex-col gap-1">
          <p className="text-sm font-semibold text-stone-600">Než začnete</p>
          <h1 className="font-serif text-3xl font-bold text-stone-950">{game.title}</h1>
        </header>

        {game.introAudioUrl && (
          <section className={`${card} flex flex-col gap-2`}>
            <h2 className="text-lg font-bold text-stone-950">🔊 Hra mluví</h2>
            <p className="text-base text-stone-700">
              Příběh vám vypráví namluvený hlas. Zapněte zvuk telefonu, vypněte tichý režim a zesilte hlasitost. Nejlépe
              uslyšíte ve sluchátkách. Text máte vždy i na obrazovce.
            </p>
          </section>
        )}

        <section className={`${card} flex flex-col gap-2`}>
          <h2 className="text-lg font-bold text-stone-950">Bezpečně a ohleduplně</h2>
          <ul className="list-disc space-y-1 pl-5 text-base text-stone-700">
            <li>Při chůzi se dívejte kolem sebe, ne do mobilu, hlavně na schodech.</li>
            <li>Hra vás nikdy nepošle do uzavřených prostor ani na zdi. Řiďte se návštěvním řádem památky.</li>
            <li>Na děti dohlíží jejich doprovod.</li>
          </ul>
        </section>

        <div className="flex flex-col gap-3">
          <button
            type="button"
            onClick={() => {
              // An explicit choice of sound turns the voice back on if it was muted in an earlier game
              voicePreference.setMuted(false)
              setPrepared(true)
            }}
            className={buttonPrimary}
          >
            {game.introAudioUrl ? 'Pustit úvod' : 'Pokračovat'}
          </button>
          {game.introAudioUrl && (
            <button
              type="button"
              onClick={() => {
                voicePreference.setMuted(true)
                setPrepared(true)
              }}
              className={buttonSecondary}
            >
              Hrát bez zvuku
            </button>
          )}
        </div>
      </Page>
    )
  }

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
            void downloadAndPlay(state.token)
          } else {
            navigate('/play', { replace: true })
          }
        },
      },
    )
  }

  /** The game into the phone for playing without a signal; without the download it still works online. */
  async function downloadAndPlay(newToken: string) {
    setDownload({ done: 0, total: 0 })
    try {
      await downloadGame(newToken, (done, total) => setDownload({ done, total }))
    } catch {
      // No signal right now: the game goes on online and the package can be downloaded later
    }
    navigate('/play', { replace: true })
  }

  if (download) {
    const percent = download.total > 0 ? Math.round((download.done / download.total) * 100) : 0
    return (
      <Page>
        <header className="flex flex-col gap-1">
          <p className="text-sm font-semibold text-stone-600">Připravuji hru</p>
          <h1 className="font-serif text-3xl font-bold text-stone-950">{game.title}</h1>
        </header>
        <p className="text-base text-stone-700">
          Stahuji příběh, obrázky a nahrávky do telefonu, abyste mohli hrát i tam, kde není signál.
        </p>
        <div
          role="progressbar"
          aria-label="Stahování hry"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={percent}
          className="h-2.5 w-full overflow-hidden rounded-full bg-stone-300"
        >
          <div className="h-full rounded-full bg-amber-600 transition-[width]" style={{ width: `${percent}%` }} />
        </div>
        <p className="text-sm text-stone-600">
          {download.total > 0 ? `${download.done} z ${download.total} souborů` : 'Začínám…'}
        </p>
      </Page>
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
