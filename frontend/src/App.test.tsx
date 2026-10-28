import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.tsx'
import type { SessionState } from './api/types.ts'
import { createQueryClient } from './queryClient.ts'
import SessionTokenProvider from './session/SessionTokenProvider.tsx'
import { tokenStorage } from './session/tokenStorage.ts'

const TOKEN = '6f1c2a9e-1111-4222-8333-944455556666'

const inProgress: SessionState = {
  status: 'IN_PROGRESS',
  game: { id: 'g1', title: 'Tajemství katova klíče' },
  teamName: null,
  startedAt: '2026-10-28T10:00:00Z',
  penaltySeconds: 0,
  progress: { solved: 1, total: 5 },
  currentStation: {
    id: 's2',
    orderNo: 2,
    title: 'Hradní věž',
    storyText: 'Strážný u věže vzpomíná.',
    taskText: 'Kolik schodů kat vystoupal až nahoru?',
    taskType: 'NUMBER',
    choices: null,
    hintsAvailable: 2,
    nextHintPenaltySeconds: 60,
    revealedHints: [],
  },
  solvedStations: [{ id: 's1', orderNo: 1, title: 'Katova světnice', rewardText: 'Písmeno K.' }],
  finale: null,
}

const demoGame = {
  id: 'g1',
  title: 'Tajemství katova klíče',
  subtitle: 'Demo hra',
  introText: 'Píše se rok 1621.',
  estimatedMinutes: 20,
  stationCount: 5,
}

type Handler = (init?: RequestInit) => Response

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** Mocks fetch by "METHOD /api/v1/path"; unknown requests fail the test. */
function mockApi(handlers: Record<string, Handler>) {
  const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(url)}`
    const handler = handlers[key]
    if (!handler) {
      throw new Error(`Unexpected request ${key}`)
    }
    return handler(init)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderApp(route: string) {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <SessionTokenProvider>
        <MemoryRouter initialEntries={[route]}>
          <App />
        </MemoryRouter>
      </SessionTokenProvider>
    </QueryClientProvider>,
  )
}

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
  })

  it('starts a game with a new code: intro, team name, first station', async () => {
    const fetchMock = mockApi({
      'POST /api/v1/access-codes/check': () =>
        json({ code: 'ABCD-2345', game: demoGame, resumesGame: false, validUntil: null }),
      'POST /api/v1/sessions': () => json({ ...inProgress, token: TOKEN }, 201),
      'GET /api/v1/sessions/current': () => json(inProgress),
    })
    renderApp('/')

    expect(screen.queryByRole('link', { name: 'Pokračovat ve hře' })).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Zadej kód z kartičky'), 'abcd 2345{Enter}')

    expect(await screen.findByRole('heading', { name: 'Tajemství katova klíče' })).toBeInTheDocument()
    expect(screen.getByText('Píše se rok 1621.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/Název týmu/), 'Kuchtovi')
    await userEvent.click(screen.getByRole('button', { name: 'Začít hru' }))

    expect(await screen.findByRole('heading', { name: 'Hradní věž' })).toBeInTheDocument()
    expect(tokenStorage.get()).toBe(TOKEN)
    const bodies = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST').map(([, init]) => init?.body)
    expect(bodies).toEqual(['{"code":"abcd 2345"}', '{"code":"ABCD-2345","teamName":"Kuchtovi"}'])
  })

  it('continues the game of a code that is already in use', async () => {
    mockApi({
      'POST /api/v1/access-codes/check': () =>
        json({ code: 'ABCD-2345', game: demoGame, resumesGame: true, validUntil: '2026-10-28T15:00:00Z' }),
      'POST /api/v1/sessions': () => json({ ...inProgress, token: TOKEN }),
      'GET /api/v1/sessions/current': () => json(inProgress),
    })
    renderApp('/?kod=ABCD-2345')

    expect(screen.getByLabelText('Zadej kód z kartičky')).toHaveValue('ABCD-2345')
    await userEvent.click(screen.getByRole('button', { name: 'Použít kód' }))

    expect(await screen.findByRole('heading', { name: 'Hradní věž' })).toBeInTheDocument()
    expect(tokenStorage.get()).toBe(TOKEN)
  })

  it('shows why a code cannot be used', async () => {
    mockApi({
      'POST /api/v1/access-codes/check': () =>
        json({ type: '/problems/code-expired', status: 410, detail: 'Platnost tohoto kódu už vypršela.' }, 410),
    })
    renderApp('/')

    await userEvent.type(screen.getByLabelText('Zadej kód z kartičky'), 'ABCD-2345{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent('Platnost tohoto kódu už vypršela.')
    expect(tokenStorage.get()).toBeNull()
  })

  it('offers to continue a stored game', async () => {
    tokenStorage.set(TOKEN)
    mockApi({})
    renderApp('/')

    expect(screen.getByRole('link', { name: 'Pokračovat ve hře' })).toHaveAttribute('href', '/play')
  })

  it('sends a player without a game from /play to the home page', async () => {
    mockApi({})
    renderApp('/play')

    expect(await screen.findByRole('heading', { name: 'Zážitkový Loket' })).toBeInTheDocument()
  })

  it('restores the current station after reload and sends the token', async () => {
    tokenStorage.set(TOKEN)
    const fetchMock = mockApi({ 'GET /api/v1/sessions/current': () => json(inProgress) })
    renderApp('/play')

    expect(await screen.findByRole('heading', { name: 'Hradní věž' })).toBeInTheDocument()
    expect(screen.getByText('Stanoviště 2 / 5')).toBeInTheDocument()
    expect(screen.getByLabelText('Tvoje odpověď')).toHaveAttribute('inputmode', 'numeric')
    const headers = fetchMock.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers['X-Session-Token']).toBe(TOKEN)
  })

  it('shows the reward after a correct answer', async () => {
    tokenStorage.set(TOKEN)
    mockApi({
      'GET /api/v1/sessions/current': () => json(inProgress),
      'POST /api/v1/sessions/current/stations/s2/answers': () =>
        json({ correct: true, rewardText: 'Na 56. schodu je písmeno L.', nextStationId: 's3', gameCompleted: false }),
    })
    renderApp('/play')

    await userEvent.type(await screen.findByLabelText('Tvoje odpověď'), '56{Enter}')

    expect(await screen.findByText('Na 56. schodu je písmeno L.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Na další stanoviště' })).toHaveAttribute('href', '/play')
  })

  it('shows the reward of the last station before the finale', async () => {
    tokenStorage.set(TOKEN)
    const lastStation = { ...inProgress.currentStation!, id: 's5', orderNo: 5, title: 'Tajenka', taskType: 'TEXT' as const }
    let completed = false
    mockApi({
      'GET /api/v1/sessions/current': () =>
        json(
          completed
            ? {
                ...inProgress,
                status: 'COMPLETED',
                progress: { solved: 5, total: 5 },
                currentStation: null,
                finale: { outroText: 'Klíč ležel pod prahem.', totalSeconds: 900, hintsUsed: 0 },
              }
            : { ...inProgress, progress: { solved: 4, total: 5 }, currentStation: lastStation },
        ),
      'POST /api/v1/sessions/current/stations/s5/answers': () => {
        completed = true
        return json({ correct: true, rewardText: 'Klíč! A teď už víš, kde ho hledat…', gameCompleted: true })
      },
    })
    renderApp('/play')

    await userEvent.type(await screen.findByLabelText('Tvoje odpověď'), 'klíč{Enter}')

    expect(await screen.findByText('Klíč! A teď už víš, kde ho hledat…')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Na závěr příběhu' }))
    expect(await screen.findByText('Klíč ležel pod prahem.')).toBeInTheDocument()
  })

  it('shows the message after a wrong answer and stays on the station', async () => {
    tokenStorage.set(TOKEN)
    mockApi({
      'GET /api/v1/sessions/current': () => json(inProgress),
      'POST /api/v1/sessions/current/stations/s2/answers': () =>
        json({ correct: false, message: 'To není ono. Zkus to znovu.' }),
    })
    renderApp('/play')

    await userEvent.type(await screen.findByLabelText('Tvoje odpověď'), '60{Enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent('To není ono. Zkus to znovu.')
    expect(screen.getByRole('heading', { name: 'Hradní věž' })).toBeInTheDocument()
  })

  it('clears an expired game and returns home with the server message', async () => {
    tokenStorage.set(TOKEN)
    mockApi({
      'GET /api/v1/sessions/current': () =>
        json({ type: '/problems/session-expired', status: 410, detail: 'Platnost rozehrané hry vypršela.' }, 410),
    })
    renderApp('/play')

    expect(await screen.findByRole('heading', { name: 'Zážitkový Loket' })).toBeInTheDocument()
    expect(screen.getByText('Platnost rozehrané hry vypršela.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Pokračovat ve hře' })).not.toBeInTheDocument()
    expect(tokenStorage.get()).toBeNull()
  })

  it('shows the finale summary for a completed game', async () => {
    tokenStorage.set(TOKEN)
    mockApi({
      'GET /api/v1/sessions/current': () =>
        json({
          ...inProgress,
          status: 'COMPLETED',
          teamName: 'Kuchtovi',
          penaltySeconds: 240,
          progress: { solved: 5, total: 5 },
          currentStation: null,
          finale: { outroText: 'Klíč ležel pod prahem.', totalSeconds: 1500, hintsUsed: 3 },
        }),
    })
    renderApp('/play')

    expect(await screen.findByText('Klíč ležel pod prahem.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Výsledek týmu Kuchtovi' })).toBeInTheDocument()
    expect(screen.getByText('25 min')).toBeInTheDocument()
    expect(screen.getByText('4 min')).toBeInTheDocument()
  })
})
