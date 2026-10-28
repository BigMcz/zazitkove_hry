import { Link, useLocation } from 'react-router'
import { useGames } from '../api/queries.ts'
import ErrorBanner from '../components/ErrorBanner.tsx'
import Page from '../components/Page.tsx'
import { buttonPrimary, card } from '../components/ui.ts'
import { useSessionToken } from '../session/sessionTokenContext.ts'
import type { HomeLocationState } from '../session/usePlaySession.ts'

export default function HomePage() {
  const { token } = useSessionToken()
  const location = useLocation()
  const message = (location.state as HomeLocationState | null)?.message
  const games = useGames()

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

      <section aria-labelledby="games-heading" className="flex flex-col gap-3">
        <h2 id="games-heading" className="text-xl font-bold">
          Vyber si hru
        </h2>

        {games.isPending && <p role="status">Načítám hry…</p>}

        {games.isError && <ErrorBanner message={games.error.message} onRetry={() => void games.refetch()} />}

        {games.data?.length === 0 && <p>Zatím tu není žádná hra.</p>}

        <ul className="flex flex-col gap-3">
          {games.data?.map((game) => (
            <li key={game.id}>
              <Link
                to={`/games/${game.id}`}
                className={`${card} flex flex-col gap-1 hover:border-stone-900 focus-visible:outline-3 focus-visible:outline-amber-500`}
              >
                <span className="font-serif text-2xl font-bold text-stone-950">{game.title}</span>
                {game.subtitle && <span className="text-base text-stone-700">{game.subtitle}</span>}
                {game.estimatedMinutes && (
                  <span className="text-sm font-semibold text-stone-600">cca {game.estimatedMinutes} min</span>
                )}
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </Page>
  )
}
