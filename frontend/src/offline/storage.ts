import type { GamePackage } from './types.ts'

/** An action played in the phone that the server has not got yet (sent by playClient). */
export type QueuedEvent =
  | { id: string; type: 'answer'; stationId: string; answer: string; at: string }
  | { id: string; type: 'hint'; stationId: string; at: string }

/** The game as played in this phone (docs/02-architektura.md, "Offline režim"). */
export interface LocalProgress {
  /** Solved stations in order with their content keys (base64), also those solved on another phone. */
  solved: { stationId: string; rawKey: string; solvedAt: string }[]
  /** Number of revealed hints per station. */
  hints: Record<string, number>
  finishedAt: string | null
  queue: QueuedEvent[]
}

export function emptyProgress(): LocalProgress {
  return { solved: [], hints: {}, finishedAt: null, queue: [] }
}

/**
 * Value in localStorage tied to the session token. Storage may be unavailable (private mode), then the game
 * simply needs a signal.
 */
function tokenBound<T>(key: string) {
  return {
    get(token: string): T | null {
      try {
        const stored = JSON.parse(window.localStorage.getItem(key) ?? 'null') as { token: string; value: T } | null
        return stored?.token === token ? stored.value : null
      } catch {
        return null
      }
    },

    set(token: string, value: T): void {
      try {
        window.localStorage.setItem(key, JSON.stringify({ token, value }))
      } catch {
        // see above
      }
    },

    clear(): void {
      try {
        window.localStorage.removeItem(key)
      } catch {
        // see above
      }
    },
  }
}

export const packageStorage = tokenBound<GamePackage>('zazitky.gamePackage')

const progressStore = tokenBound<LocalProgress>('zazitky.gameProgress')

export const progressStorage = {
  ...progressStore,

  /** Reads the latest stored progress, changes it and stores it, so that concurrent actions do not overwrite each other. */
  update(token: string, change: (progress: LocalProgress) => LocalProgress): LocalProgress {
    const updated = change(progressStore.get(token) ?? emptyProgress())
    progressStore.set(token, updated)
    return updated
  },
}

/** The downloaded game belongs to the session; called when the token is cleared. */
export function clearOfflineGame(): void {
  packageStorage.clear()
  progressStorage.clear()
}
