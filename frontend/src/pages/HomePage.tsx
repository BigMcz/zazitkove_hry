import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api/client.ts'
import { useStartSession } from '../api/queries.ts'
import ErrorBanner from '../components/ErrorBanner.tsx'
import Page from '../components/Page.tsx'
import { buttonPrimary, buttonSecondary, card, input } from '../components/ui.ts'
import { useSessionToken } from '../session/sessionTokenContext.ts'
import type { HomeLocationState } from '../session/usePlaySession.ts'
import type { StartLocationState } from './StartPage.tsx'

export default function HomePage() {
  const { token, setToken } = useSessionToken()
  const location = useLocation()
  const navigate = useNavigate()
  const message = (location.state as HomeLocationState | null)?.message
  // A QR code on the card links to /?kod=ABCD-2345
  const [searchParams] = useSearchParams()
  const [code, setCode] = useState(searchParams.get('kod') ?? '')
  const checkCode = useMutation({ mutationFn: api.checkCode })
  const startSession = useStartSession()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const typed = code.trim()
    if (typed === '') {
      return
    }
    checkCode.mutate(typed, {
      onSuccess: (check) => {
        if (!check.resumesGame) {
          const state: StartLocationState = { check }
          navigate('/start', { state })
          return
        }
        // The code is already in use (e.g. on another phone): continue that game.
        startSession.mutate(
          { code: check.code, teamName: null },
          {
            onSuccess: (session) => {
              if (session.token) {
                setToken(session.token)
              }
              navigate('/play')
            },
          },
        )
      },
    })
  }

  const error = checkCode.error ?? startSession.error
  const pending = checkCode.isPending || startSession.isPending

  return (
    <Page>
      <header className="flex flex-col gap-1">
        <h1 className="font-serif text-4xl font-bold text-stone-950">Zážitkový Loket</h1>
        <p className="text-lg text-stone-700">Příběhové hry na hradech a zámcích.</p>
      </header>

      {message && <ErrorBanner tone="info" message={message} />}

      {token && (
        <section className={`${card} flex flex-col gap-3 border-2 border-stone-900`}>
          <h2 className="text-xl font-bold">Máš rozehranou hru</h2>
          <Link to="/play" className={buttonPrimary}>
            Pokračovat ve hře
          </Link>
        </section>
      )}

      <form onSubmit={handleSubmit} className={`${card} flex flex-col gap-3`}>
        <label htmlFor="code" className="text-xl font-bold">
          Zadej kód z kartičky
        </label>
        <input
          id="code"
          name="code"
          type="text"
          inputMode="text"
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="characters"
          spellCheck={false}
          enterKeyHint="go"
          maxLength={30}
          placeholder="ABCD-2345"
          value={code}
          onChange={(event) => setCode(event.target.value)}
          className={`${input} font-mono tracking-widest uppercase`}
        />
        {error && <ErrorBanner message={error.message} />}
        <button type="submit" disabled={pending || code.trim() === ''} className={token ? buttonSecondary : buttonPrimary}>
          {pending ? 'Ověřuji…' : 'Použít kód'}
        </button>
        <p className="text-sm text-stone-600">Kód najdeš na kartičce z pokladny hradu.</p>
      </form>
    </Page>
  )
}
