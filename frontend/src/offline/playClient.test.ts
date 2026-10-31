import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SessionState } from '../api/types.ts'
import { sealedPackage } from '../test/sealedPackage.ts'
import { forgetOfflineStatus } from './gameDownload.ts'
import { playClient } from './playClient.ts'
import { packageStorage, progressStorage } from './storage.ts'

const TOKEN = '6f1c2a9e-1111-4222-8333-944455556666'

async function downloaded() {
  const pack = await sealedPackage(
    [
      { id: 's1', title: 'Brána', taskType: 'TEXT', answers: ['ohyb reky'], hints: [60] },
      { id: 's2', title: 'Věž', taskType: 'NUMBER', answers: ['56'] },
    ],
    { expiresAt: '2999-01-01T00:00:00Z' },
  )
  packageStorage.set(TOKEN, pack)
  return pack
}

function serverState(solved: string[]): SessionState {
  return {
    status: 'IN_PROGRESS',
    game: { id: 'g1', title: 'Testovací hra' },
    teamName: null,
    startedAt: '2026-10-31T10:00:00Z',
    penaltySeconds: 0,
    progress: { solved: solved.length, total: 2 },
    currentStation: null,
    solvedStations: solved.map((id, index) => ({
      id,
      orderNo: index + 1,
      title: id,
      rewardText: '',
      rewardAudioUrl: null,
      nextText: null,
    })),
    puzzle: null,
    finale: null,
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

afterEach(() => {
  vi.unstubAllGlobals()
  window.localStorage.clear()
  forgetOfflineStatus()
})

describe('playClient', () => {
  it('plays from the package without a signal and keeps the actions for the server', async () => {
    await downloaded()
    const fetchMock = vi.fn(() => Promise.reject(new TypeError('Failed to fetch')))
    vi.stubGlobal('fetch', fetchMock)

    expect((await playClient.getState(TOKEN)).currentStation?.id).toBe('s1')
    expect((await playClient.revealHint(TOKEN, 's1')).penaltySeconds).toBe(60)
    expect((await playClient.submitAnswer(TOKEN, 's1', 'nevím')).correct).toBe(false)
    expect(await playClient.submitAnswer(TOKEN, 's1', 'Ohyb řeky')).toMatchObject({ correct: true, nextStationId: 's2' })

    const state = await playClient.getState(TOKEN)
    expect(state.currentStation?.id).toBe('s2')
    expect(state.penaltySeconds).toBe(60)
    expect(progressStorage.get(TOKEN)?.queue.map((event) => event.type)).toEqual(['hint', 'answer', 'answer'])
  })

  it('sends the queued actions in one request with the times they were played', async () => {
    await downloaded()
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    await playClient.revealHint(TOKEN, 's1')
    await playClient.submitAnswer(TOKEN, 's1', 'nevím')
    await playClient.submitAnswer(TOKEN, 's1', 'ohyb reky')
    const queued = progressStorage.get(TOKEN)!.queue

    const sent: unknown[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url === '/api/v1/sessions/current/sync') {
          sent.push(JSON.parse(String(init?.body)))
          return json(serverState(['s1']))
        }
        return json(serverState(['s1']))
      }),
    )

    const state = await playClient.getState(TOKEN)

    expect(sent).toEqual([
      {
        events: [
          { id: queued[0].id, type: 'HINT', stationId: 's1', at: queued[0].at },
          { id: queued[1].id, type: 'ANSWER', stationId: 's1', answer: 'nevím', at: queued[1].at },
          { id: queued[2].id, type: 'ANSWER', stationId: 's1', answer: 'ohyb reky', at: queued[2].at },
        ],
      },
    ])
    expect(progressStorage.get(TOKEN)?.queue).toEqual([])
    expect(state.currentStation?.id).toBe('s2')
  })

  it('keeps the queue while there is no signal and drops it once the server took it after the code expired', async () => {
    await downloaded()
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))
    await playClient.submitAnswer(TOKEN, 's1', 'ohyb reky')
    await playClient.getState(TOKEN)
    expect(progressStorage.get(TOKEN)?.queue).toHaveLength(1)

    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        json({ type: '/problems/session-expired', status: 410, detail: 'Platnost rozehrané hry vypršela.' }, 410),
      ),
    )
    await expect(playClient.getState(TOKEN)).rejects.toMatchObject({ status: 410 })
    expect(progressStorage.get(TOKEN)?.queue).toEqual([])
  })

  it('takes over a station solved on another phone', async () => {
    const pack = await downloaded()
    // The key of s1 comes from a fresh package, as the server sends it for solved stations
    const { solveStation } = await import('./packageCrypto.ts')
    const solved = await solveStation(pack.stations[0], 'ohyb reky', pack.kdfIterations)
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) =>
        url.endsWith('/package')
          ? json({ ...pack, unlockedKeys: { s1: solved!.rawKey } })
          : json(serverState(['s1'])),
      ),
    )

    const state = await playClient.getState(TOKEN)

    expect(state.currentStation?.id).toBe('s2')
    expect(state.solvedStations.map((station) => station.rewardText)).toEqual(['Odměna Brána'])
  })

  it('ends an expired game like the server does', async () => {
    const pack = await downloaded()
    packageStorage.set(TOKEN, { ...pack, session: { ...pack.session, expiresAt: '2000-01-01T00:00:00Z' } })
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))))

    await expect(playClient.getState(TOKEN)).rejects.toMatchObject({ status: 410 })
  })
})
