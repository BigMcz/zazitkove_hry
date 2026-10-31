import { afterEach, describe, expect, it, vi } from 'vitest'
import { MEDIA_CACHE, downloadGame } from './gameDownload.ts'
import { packageStorage } from './storage.ts'

const TOKEN = '6f1c2a9e-1111-4222-8333-944455556666'

/** Cache API (not in jsdom) with what is already in the phone. */
function stubCaches(alreadyCached: string[]) {
  const stored = new Map<string, Response>(alreadyCached.map((url) => [url, new Response('old')]))
  const cache = {
    match: vi.fn(async (url: string) => stored.get(url)),
    put: vi.fn(async (url: string, response: Response) => {
      stored.set(url, response)
    }),
  }
  vi.stubGlobal('caches', { open: vi.fn(async (name: string) => (name === MEDIA_CACHE ? cache : undefined)) })
  return stored
}

afterEach(() => {
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

describe('downloadGame', () => {
  it('stores the package and caches the missing files in order, several at a time', async () => {
    const media = ['/images/demo/cover.webp', '/a1.mp3', '/a2.mp3', '/a3.mp3', '/a4.mp3', '/a5.mp3', '/zaver.mp3']
    const stored = stubCaches(['/images/demo/cover.webp'])
    const started: string[] = []
    let running = 0
    let mostAtOnce = 0
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url === '/api/v1/sessions/current/package') {
          return new Response(JSON.stringify({ gameId: 'g1', media }), { status: 200 })
        }
        started.push(url)
        running++
        mostAtOnce = Math.max(mostAtOnce, running)
        await new Promise((resolve) => setTimeout(resolve, 5))
        running--
        return url === '/a5.mp3' ? new Response('', { status: 404 }) : new Response('mp3', { status: 200 })
      }),
    )
    const progress: [number, number][] = []

    await downloadGame(TOKEN, (done, total) => progress.push([done, total]))

    expect(packageStorage.get(TOKEN)?.media).toEqual(media)
    // Already cached is not downloaded again; a missing file is skipped
    expect(started).toEqual(['/a1.mp3', '/a2.mp3', '/a3.mp3', '/a4.mp3', '/a5.mp3', '/zaver.mp3'])
    expect(mostAtOnce).toBe(4)
    expect([...stored.keys()].sort()).toEqual(media.filter((url) => url !== '/a5.mp3').sort())
    expect(progress.at(-1)).toEqual([7, 7])
  })
})
