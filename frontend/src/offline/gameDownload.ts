import { useSyncExternalStore } from 'react'
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

/** Files downloaded at the same time: on mobile data the waiting for each response adds up one by one. */
const PARALLEL_DOWNLOADS = 4

/** Downloads the files in the given order (the nearest stations first), skipping those already in the phone. */
async function cacheMedia(urls: string[], onProgress?: DownloadProgress): Promise<void> {
  onProgress?.(0, urls.length)
  if (!('caches' in window)) {
    return
  }
  const cache = await caches.open(MEDIA_CACHE)
  const waiting = [...urls]
  let done = 0
  async function downloadNext(): Promise<void> {
    for (let url = waiting.shift(); url !== undefined; url = waiting.shift()) {
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
  await Promise.all(Array.from({ length: Math.min(PARALLEL_DOWNLOADS, urls.length) }, downloadNext))
}

/** How far the phone is with preparing the game for playing without a signal; null = not known in this visit. */
export type OfflineStatus = { phase: 'downloading'; done: number; total: number } | { phase: 'ready' } | null

const statuses = new Map<string, OfflineStatus>()
const listeners = new Set<() => void>()
const started = new Set<string>()

function setStatus(token: string, status: OfflineStatus): void {
  statuses.set(token, status)
  listeners.forEach((listener) => listener())
}

/**
 * Prepares the game for playing without a signal in the background, once per visit: downloads the package, or for a
 * package already in the phone the pictures and sounds still missing (download interrupted by closing the browser).
 * Without a signal it quietly gives up and tries again the next time the game state is loaded.
 */
export function prepareOffline(token: string): void {
  if (started.has(token)) {
    return
  }
  started.add(token)
  const onProgress: DownloadProgress = (done, total) => setStatus(token, { phase: 'downloading', done, total })
  const stored = packageStorage.get(token)
  const work = stored ? cacheMedia(stored.media, onProgress) : downloadGame(token, onProgress)
  work.then(
    () => setStatus(token, { phase: 'ready' }),
    () => {
      setStatus(token, null)
      started.delete(token)
    },
  )
}

/** Forgets the preparation of the games (the game was left), so that a new game starts with a clean state. */
export function forgetOfflineStatus(): void {
  started.clear()
  statuses.clear()
  listeners.forEach((listener) => listener())
}

export function useOfflineStatus(token: string | null): OfflineStatus {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    () => (token ? (statuses.get(token) ?? null) : null),
  )
}
