import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { api } from '../api/client.ts'
import { useStartSession } from '../api/queries.ts'
import type { CodeCheck, GameDetail } from '../api/types.ts'
import ErrorBanner from '../components/ErrorBanner.tsx'
import Page from '../components/Page.tsx'
import StoryImage from '../components/StoryImage.tsx'
import StoryText from '../components/StoryText.tsx'
import VoicePlayer from '../components/VoicePlayer.tsx'
import { voicePreference } from '../components/audioPreference.ts'
import { buttonChoice, buttonPrimary, buttonSecondary, card, input, linkSubtle } from '../components/ui.ts'
import { gameTexts } from '../i18n/gameTexts.ts'
import { prepareOffline } from '../offline/gameDownload.ts'
import { useSessionToken } from '../session/sessionTokenContext.ts'

export interface StartLocationState {
  check: CodeCheck
}

/**
 * Intro of the game the code belongs to; starting the game activates the code. A game in more languages first
 * asks for the language, the rest of the game is then in it. Before the intro the player reads about the sound and
 * safety: the spoken intro would otherwise start on a muted phone unnoticed, and the tap on "Pustit úvod" lets the
 * browser play it.
 */
export default function StartPage() {
  const location = useLocation()
  const check = (location.state as StartLocationState | null)?.check
  const versions = check ? [check.game, ...(check.languageVersions ?? [])] : []
  const [chosen, setChosen] = useState<GameDetail | null>(versions.length === 1 ? versions[0] : null)

  // The code is kept only in the navigation state (not in the URL); after a reload start again.
  if (!check) {
    return <Navigate to="/" replace />
  }
  if (!chosen) {
    return <LanguageChoice versions={versions} onChoose={setChosen} />
  }
  return (
    <GameStart
      // A new start (sound, team name) for each language
      key={chosen.id}
      check={check}
      game={chosen}
      onBack={versions.length > 1 ? () => setChosen(null) : null}
    />
  )
}

/** In both languages: the player does not know yet which one the game will speak. */
function LanguageChoice({ versions, onChoose }: { versions: GameDetail[]; onChoose: (game: GameDetail) => void }) {
  const cover = versions[0].coverImageUrl
  return (
    <Page>
      <Link to="/" className={linkSubtle}>
        ← {versions.map((game) => gameTexts(game.language).cancel).join(' · ')}
      </Link>
      <header className="flex flex-col gap-1">
        <h1 className="font-serif text-3xl font-bold text-stone-950">
          {versions.map((game) => <LanguageName key={game.id} game={game} />)}
        </h1>
      </header>
      {cover && <StoryImage src={cover} alt="" />}
      <div role="group" aria-label="Jazyk hry · Sprache des Spiels" className="flex flex-col gap-3">
        {versions.map((game) => {
          const texts = gameTexts(game.language)
          return (
            <button
              key={game.id}
              type="button"
              lang={game.language}
              onClick={() => onChoose(game)}
              className={`${buttonChoice} justify-start gap-4 py-3 text-left`}
            >
              <img
                src={texts.flagUrl}
                alt=""
                className="h-8 w-12 shrink-0 rounded-sm object-cover shadow-[0_0_0_1px_rgb(0_0_0/0.15)]"
              />
              <span className="flex flex-col">
                <span className="font-bold">{texts.languageName}</span>
                <span className="text-base font-normal text-stone-700">{game.title}</span>
              </span>
            </button>
          )
        })}
      </div>
    </Page>
  )
}

/** "Vyberte jazyk" / "Sprache wählen" in the language of each version. */
function LanguageName({ game }: { game: GameDetail }) {
  return (
    <span lang={game.language} className="block">
      {gameTexts(game.language).chooseLanguage}
    </span>
  )
}

interface GameStartProps {
  check: CodeCheck
  game: GameDetail
  /** Back to the choice of the language, null = the game has only one. */
  onBack: (() => void) | null
}

function GameStart({ check, game, onBack }: GameStartProps) {
  const navigate = useNavigate()
  const { token, setToken } = useSessionToken()
  const startSession = useStartSession()
  const [teamName, setTeamName] = useState('')
  const [prepared, setPrepared] = useState(false)
  const texts = gameTexts(game.language)

  const back = onBack ? (
    <button type="button" onClick={onBack} className={`${linkSubtle} self-start`}>
      {texts.back}
    </button>
  ) : (
    <Link to="/" className={linkSubtle}>
      {texts.back}
    </Link>
  )

  if (!prepared) {
    return (
      <Page texts={texts}>
        {back}
        <header className="flex flex-col gap-1">
          <p className="text-sm font-semibold text-stone-600">{texts.beforeStart}</p>
          <h1 className="font-serif text-3xl font-bold text-stone-950">{game.title}</h1>
        </header>

        {game.introAudioUrl && (
          <section className={`${card} flex flex-col gap-2`}>
            <h2 className="text-lg font-bold text-stone-950">{texts.voiceHeading}</h2>
            <p className="text-base text-stone-700">{texts.voiceInfo}</p>
          </section>
        )}

        <section className={`${card} flex flex-col gap-2`}>
          <h2 className="text-lg font-bold text-stone-950">{texts.safetyHeading}</h2>
          <ul className="list-disc space-y-1 pl-5 text-base text-stone-700">
            {texts.safetyRules.map((rule) => (
              <li key={rule}>{rule}</li>
            ))}
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
            {game.introAudioUrl ? texts.playIntro : texts.continue}
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
              {texts.playMuted}
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
      { code: check.code, teamName: teamName.trim() || null, language: game.language },
      {
        onSuccess: (state) => {
          if (state.token) {
            setToken(state.token)
            // The game downloads into the phone in the background, the player does not wait for it
            prepareOffline(state.token)
          }
          navigate('/play', { replace: true })
        },
      },
    )
  }

  return (
    <Page texts={texts}>
      {back}

      <header className="flex flex-col gap-1">
        <h1 className="font-serif text-3xl font-bold text-stone-950">{game.title}</h1>
        {game.subtitle && <p className="text-lg text-stone-700">{game.subtitle}</p>}
        <p className="text-sm font-semibold text-stone-600">
          {texts.stations(game.stationCount)}
          {game.estimatedMinutes && ` · ${texts.aboutMinutes(game.estimatedMinutes)}`}
        </p>
      </header>

      {game.coverImageUrl && <StoryImage src={game.coverImageUrl} alt="" />}
      {game.introAudioUrl && <VoicePlayer src={game.introAudioUrl} />}
      <StoryText text={game.introText} />

      <form onSubmit={(event) => void handleStart(event)} className="flex flex-col gap-3">
        <label htmlFor="team-name" className="text-base font-semibold text-stone-800">
          {game.forChildren ? texts.diplomaName : texts.teamName}{' '}
          <span className="font-normal text-stone-600">{texts.optional}</span>
        </label>
        {game.forChildren && (
          <p id="team-name-hint" className="-mt-2 text-sm text-stone-600">
            {texts.diplomaNameHint}
          </p>
        )}
        <input
          id="team-name"
          type="text"
          autoComplete="off"
          aria-describedby={game.forChildren ? 'team-name-hint' : undefined}
          maxLength={100}
          value={teamName}
          onChange={(event) => setTeamName(event.target.value)}
          className={input}
        />
        {token && <p className="text-base text-stone-700">{texts.newGameEndsRunning}</p>}
        <p className="text-sm text-stone-600">
          {texts.codeValidity[0]}
          <span className="font-mono whitespace-nowrap">{check.code}</span>
          {texts.codeValidity[1]}
        </p>
        {startSession.isError && <ErrorBanner message={texts.error(startSession.error)} />}
        <button type="submit" disabled={startSession.isPending} className={buttonPrimary}>
          {startSession.isPending ? texts.starting : texts.startGame}
        </button>
      </form>
    </Page>
  )
}
