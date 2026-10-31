import { QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.tsx'
import type { SessionState } from './api/types.ts'
import { packageStorage } from './offline/storage.ts'
import type { GamePackage } from './offline/types.ts'
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
    storyAudioUrl: null,
    listenOnly: false,
    imageUrl: null,
    imageAlt: null,
    taskText: 'Kolik schodů kat vystoupal až nahoru?',
    taskType: 'NUMBER',
    choices: null,
    hintsAvailable: 2,
    nextHintPenaltySeconds: 60,
    revealedHints: [],
  },
  solvedStations: [{ id: 's1', orderNo: 1, title: 'Katova světnice', rewardText: 'Písmeno K.', rewardAudioUrl: null, nextText: 'Jdi k věži.' }],
  puzzle: { slots: [null, 'K', null, null], latestSlot: 1 },
  finale: null,
}

const demoGame = {
  id: 'g1',
  title: 'Tajemství katova klíče',
  subtitle: 'Demo hra',
  introText: 'Píše se rok 1621.',
  introAudioUrl: '/audio/demo/intro.mp3',
  coverImageUrl: '/images/demo/cover.jpg',
  estimatedMinutes: 20,
  stationCount: 5,
}

const gamePackage: GamePackage = {
  gameId: 'g1',
  session: { gameTitle: 'Tajemství katova klíče', teamName: null, startedAt: '2026-10-28T10:00:00Z', expiresAt: '2026-10-28T15:00:00Z' },
  kdfIterations: 1000,
  firstStation: { ...inProgress.currentStation!, hints: [] },
  stations: [],
  puzzleSlots: null,
  unlockedKeys: {},
  media: [],
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
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  it('starts a game with a new code: intro, team name, first station', async () => {
    const fetchMock = mockApi({
      'POST /api/v1/access-codes/check': () =>
        json({ code: 'ABCD-2345', game: demoGame, resumesGame: false, validUntil: null }),
      'POST /api/v1/sessions': () => json({ ...inProgress, token: TOKEN }, 201),
      'GET /api/v1/sessions/current/package': () => json(gamePackage),
      'GET /api/v1/sessions/current': () => json(inProgress),
    })
    renderApp('/')

    expect(screen.queryByRole('link', { name: 'Pokračovat ve hře' })).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Zadej kód z kartičky'), 'abcd 2345{Enter}')

    expect(await screen.findByRole('heading', { name: 'Tajemství katova klíče' })).toBeInTheDocument()
    // First the sound and safety notice, the intro (and its voice) only after a tap
    expect(screen.getByRole('heading', { name: '🔊 Hra mluví' })).toBeInTheDocument()
    expect(document.querySelector('audio')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Pustit úvod' }))
    expect(screen.getByText('Píše se rok 1621.')).toBeInTheDocument()
    // The spoken intro tries to start on its own; the button stays for blocked autoplay
    expect(screen.getByRole('button', { name: 'Poslechnout' })).toBeInTheDocument()
    expect(document.querySelector('audio')).toHaveAttribute('src', '/audio/demo/intro.mp3')
    expect(document.querySelector('img')).toHaveAttribute('src', '/images/demo/cover.jpg')
    await userEvent.type(screen.getByLabelText(/Název týmu/), 'Kuchtovi')
    await userEvent.click(screen.getByRole('button', { name: 'Začít hru' }))

    expect(await screen.findByRole('heading', { name: 'Hradní věž' })).toBeInTheDocument()
    expect(tokenStorage.get()).toBe(TOKEN)
    // The game was downloaded into the phone for playing without a signal
    expect(packageStorage.get(TOKEN)).toEqual(gamePackage)
    const bodies = fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST').map(([, init]) => init?.body)
    expect(bodies).toEqual(['{"code":"ABCD-2345"}', '{"code":"ABCD-2345","teamName":"Kuchtovi"}'])
  })

  it('starts the demo game from the games section without a card', async () => {
    const fetchMock = mockApi({
      'POST /api/v1/access-codes/check': () =>
        json({ code: 'DEMO-KATA', game: demoGame, resumesGame: false, validUntil: null }),
    })
    renderApp('/')

    await userEvent.click(screen.getByRole('button', { name: 'Zahrát ukázku' }))

    expect(await screen.findByRole('heading', { name: 'Tajemství katova klíče' })).toBeInTheDocument()
    expect(fetchMock.mock.calls[0][1]?.body).toBe('{"code":"DEMO-KATA"}')

    // Playing without sound remembers the muted voice, so the intro does not start talking
    await userEvent.click(screen.getByRole('button', { name: 'Hrát bez zvuku' }))
    expect(screen.getByText('Píše se rok 1621.')).toBeInTheDocument()
    expect(localStorage.getItem('zazitky.voiceMuted')).toBe('1')
  })

  it('continues the game of a code that is already in use', async () => {
    mockApi({
      'POST /api/v1/access-codes/check': () =>
        json({ code: 'ABCD-2345', game: demoGame, resumesGame: true, validUntil: '2026-10-28T15:00:00Z' }),
      'POST /api/v1/sessions': () => json({ ...inProgress, token: TOKEN }),
      'GET /api/v1/sessions/current': () => json(inProgress),
    })
    // The QR code on the card goes straight back into the game
    renderApp('/?kod=ABCD-2345')

    expect(await screen.findByRole('heading', { name: 'Hradní věž' })).toBeInTheDocument()
    expect(tokenStorage.get()).toBe(TOKEN)
  })

  it('opens a new game from the QR code link without activating the code', async () => {
    const fetchMock = mockApi({
      'POST /api/v1/access-codes/check': () =>
        json({ code: 'ABCD-2345', game: demoGame, resumesGame: false, validUntil: null }),
    })
    renderApp('/?kod=abcd2345')

    expect(await screen.findByRole('heading', { name: '🔊 Hra mluví' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/v1/access-codes/check'])
  })

  it('keeps an invalid code from the QR code link filled in with the reason', async () => {
    mockApi({
      'POST /api/v1/access-codes/check': () =>
        json({ type: '/problems/code-expired', status: 410, detail: 'Platnost tohoto kódu už vypršela.' }, 410),
    })
    renderApp('/?kod=ABCD-2345')

    expect(await screen.findByRole('alert')).toHaveTextContent('Platnost tohoto kódu už vypršela.')
    expect(screen.getByLabelText('Zadej kód z kartičky')).toHaveValue('ABCD-2345')
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

  it('shows the landing page texts and steps', async () => {
    mockApi({})
    renderApp('/')

    expect(screen.getByRole('heading', { name: /Staré zdi.*Nová pravidla hry/, level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ukažte mi, jak na to' })).toHaveAttribute('href', '#jak-to-funguje')
    expect(screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent)).toEqual([
      '01 / Kartička je váš klíč',
      '02 / Odemkněte příběh',
      '03 / Rozhlédněte se',
      'Katova povinnost',
      'Začíná to na pokladně.',
      'Nabitý mobil',
      'Sluchátka',
      'Mobilní data',
      'Pohodlná obuv',
      'Vzdělává hrou',
      'Vrací pohled kolem sebe',
      'Baví celou rodinu',
    ])
    expect(screen.getByRole('heading', { name: 'Spravujete hrad, zámek nebo jinou památku?' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Napište nám' })).toHaveAttribute(
      'href',
      expect.stringMatching(/^mailto:info@storyhold\.cz\?subject=/),
    )
    expect(screen.getByText('Kolik hra stojí a kde koupím kartičku?')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Pravidla hry' })).toHaveAttribute('href', '/pravidla')
    expect(screen.getByLabelText('Zadej kód z kartičky')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '+420 605 403 623' })).toHaveAttribute('href', 'tel:+420605403623')
  })

  it('shows the rules and a 404 page in the site layout', async () => {
    mockApi({})
    const { unmount } = renderApp('/pravidla')
    expect(screen.getByRole('heading', { name: 'Jak to u nás chodí', level: 1 })).toBeInTheDocument()
    unmount()

    renderApp('/neexistuje')
    expect(screen.getByRole('heading', { name: 'Stránka nenalezena', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Zpět na úvod' })).toHaveAttribute('href', '/')
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

    expect(await screen.findByRole('heading', { name: /Staré zdi/, level: 1 })).toBeInTheDocument()
  })

  it('restores the current station after reload and sends the token', async () => {
    tokenStorage.set(TOKEN)
    const fetchMock = mockApi({ 'GET /api/v1/sessions/current': () => json(inProgress) })
    renderApp('/play')

    expect(await screen.findByRole('heading', { name: 'Hradní věž' })).toBeInTheDocument()
    expect(screen.getByText('Stanoviště 2 / 5')).toBeInTheDocument()
    // Collected letters of the puzzle, the latest one is highlighted only on the reward page
    expect(screen.getByText('Tajenka 1 / 4')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Tajenka' }).textContent).toBe('K')
    expect(screen.getByRole('listitem', { name: 'K' })).not.toHaveAttribute('aria-current')
    expect(screen.getByLabelText('Tvoje odpověď')).toHaveAttribute('inputmode', 'numeric')
    const headers = fetchMock.mock.calls[0][1]?.headers as Record<string, string>
    expect(headers['X-Session-Token']).toBe(TOKEN)
  })

  it('shows the reward after a correct answer', async () => {
    tokenStorage.set(TOKEN)
    mockApi({
      'GET /api/v1/sessions/current': () => json(inProgress),
      'POST /api/v1/sessions/current/stations/s2/answers': () =>
        json({
          correct: true,
          rewardText: 'Na 56. schodu je písmeno L.',
          nextText: 'Sestup do sklepení.',
          nextStationId: 's3',
          gameCompleted: false,
        }),
    })
    renderApp('/play')
    const scrollTo = vi.spyOn(window, 'scrollTo')

    await userEvent.type(await screen.findByLabelText('Tvoje odpověď'), '56{Enter}')

    expect(await screen.findByText('Na 56. schodu je písmeno L.')).toBeInTheDocument()
    expect(scrollTo).toHaveBeenCalledWith(0, 0)
    expect(screen.getByRole('region', { name: 'Kam dál' })).toHaveTextContent('Sestup do sklepení.')
    expect(await screen.findByRole('listitem', { name: 'K' })).toHaveAttribute('aria-current', 'true')
    expect(screen.getByRole('link', { name: 'Na další stanoviště' })).toHaveAttribute('href', '/play')
  })

  it('hides the story of a listening task until the transcript is requested', async () => {
    tokenStorage.set(TOKEN)
    const listening = {
      ...inProgress,
      currentStation: {
        ...inProgress.currentStation!,
        storyText: 'Na věky věkův amen.',
        storyAudioUrl: '/audio/demo/st04-pribeh.mp3',
        listenOnly: true,
        imageUrl: '/images/demo/st04.jpg',
        imageAlt: 'Kat a odsouzený v cele',
      },
    }
    mockApi({ 'GET /api/v1/sessions/current': () => json(listening) })
    renderApp('/play')

    expect(await screen.findByRole('button', { name: 'Zobrazit přepis' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Kat a odsouzený v cele' })).toHaveAttribute('src', '/images/demo/st04.jpg')
    expect(screen.queryByText('Na věky věkův amen.')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Zobrazit přepis' }))
    expect(screen.getByText('Na věky věkův amen.')).toBeInTheDocument()
  })

  it('shows the story of a listening task when its recording is missing, hides a missing picture', async () => {
    tokenStorage.set(TOKEN)
    const listening = {
      ...inProgress,
      currentStation: {
        ...inProgress.currentStation!,
        storyText: 'Na věky věkův amen.',
        storyAudioUrl: '/audio/demo/st04-pribeh.mp3',
        listenOnly: true,
        imageUrl: '/images/demo/st04.jpg',
        imageAlt: 'Kat a odsouzený v cele',
      },
    }
    mockApi({ 'GET /api/v1/sessions/current': () => json(listening) })
    renderApp('/play')
    await screen.findByRole('button', { name: 'Zobrazit přepis' })

    fireEvent.error(document.querySelector('audio')!)
    fireEvent.error(screen.getByRole('img'))

    expect(screen.getByText('Na věky věkův amen.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Zobrazit přepis' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Poslechnout' })).not.toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
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

    expect(await screen.findByRole('heading', { name: /Staré zdi/, level: 1 })).toBeInTheDocument()
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
    expect(screen.getByText(/Vyřešili jste všech 5 úkolů\. Nápovědu jste potřebovali jen výjimečně/)).toBeInTheDocument()
    expect(screen.getByText('25 min')).toBeInTheDocument()
    expect(screen.getByText('4 min')).toBeInTheDocument()
  })
})
