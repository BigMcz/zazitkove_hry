import { api } from '../api/client.ts'
import { packageStorage } from './storage.ts'
import type { GamePackage } from './types.ts'

/** The same cache the service worker answers pictures and sounds from (service-worker.mjs). */
export const MEDIA_CACHE = 'game-media'

export type DownloadProgress = (done: number, total: number) => void

/**
 * Downloads the game into the phone for playing without a signal: the package and all its pictures and sounds.
 * Rejects when the server cannot be reached; a missing file is skipped (the game then shows only the text).
 */
export async function downloadGame(token: string, onProgress?: DownloadProgress): Promise<GamePackage> {
  const gamePackage = await api.getPackage(token)
  packageStorage.set(token, gamePackage)
  await cacheMedia(gamePackage.media, onProgress)
  return gamePackage
}

async function cacheMedia(urls: string[], onProgress?: DownloadProgress): Promise<void> {
  onProgress?.(0, urls.length)
  if (!('caches' in window)) {
    return
  }
  const cache = await caches.open(MEDIA_CACHE)
  let done = 0
  for (const url of urls) {
    if (!(await cache.match(url))) {
      // The whole file (200), so that the service worker can answer the Range requests of iPhone from it
      const response = await fetch(url)
      if (response.ok) {
        await cache.put(url, response)
      }
    }
    onProgress?.(++done, urls.length)
  }
}
