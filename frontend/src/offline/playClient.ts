import { ApiError, api, isSessionGone } from '../api/client.ts'
import type { AnswerResult, HintsResult, SessionState } from '../api/types.ts'
import { downloadGame } from './gameDownload.ts'
import { LocalGameError, localAnswer, localHint, localState } from './localGame.ts'
import { type LocalProgress, type QueuedEvent, packageStorage, progressStorage } from './storage.ts'
import type { SyncEvent } from './types.ts'

/**
 * The game for the /play pages (docs/02-architektura.md, "Offline režim"). With a downloaded package the phone
 * plays on its own and sends every action to the server whenever there is a signal; the server re-evaluates it.
 * Without a package (old game, private mode) everything goes to the server as before.
 */
export const playClient = {
  async getState(token: string): Promise<SessionState> {
    const pack = packageStorage.get(token)
    if (!pack) {
      const state = await api.getSession(token)
      if (state.status === 'IN_PROGRESS') {
        downloadInBackground(token)
      }
      return state
    }

    try {
      const server = await synchronize(token)
      if (server.status === 'ABANDONED') {
        return server
      }
    } catch (error) {
      if (isSessionGone(error)) {
        throw error
      }
      // No signal: the phone's own state
    }

    const state = await localState(pack, current(token), now())
    if (state.status === 'EXPIRED') {
      throw new ApiError(410, 'Platnost rozehrané hry vypršela. Začni prosím novou hru.', '/problems/session-expired')
    }
    return state
  },

  async submitAnswer(token: string, stationId: string, answer: string): Promise<AnswerResult> {
    const pack = packageStorage.get(token)
    if (!pack) {
      return api.submitAnswer(token, stationId, answer)
    }
    const at = now()
    const { result, solved, finished } = await local(() =>
      localAnswer(pack, current(token), stationId, answer, at),
    )
    progressStorage.update(token, (progress) => ({
      ...progress,
      solved: solved ? [...progress.solved, solved] : progress.solved,
      finishedAt: finished ? at.toISOString() : progress.finishedAt,
      queue: [...progress.queue, { id: crypto.randomUUID(), type: 'answer', stationId, answer, at: at.toISOString() }],
    }))
    void flushQuietly(token)
    return result
  },

  async revealHint(token: string, stationId: string): Promise<HintsResult> {
    const pack = packageStorage.get(token)
    if (!pack) {
      return api.revealHint(token, stationId)
    }
    const { result, revealed } = await local(() => localHint(pack, current(token), stationId))
    progressStorage.update(token, (progress) => ({
      ...progress,
      hints: { ...progress.hints, [stationId]: revealed },
      queue: [...progress.queue, { id: crypto.randomUUID(), type: 'hint', stationId, at: now().toISOString() }],
    }))
    void flushQuietly(token)
    return result
  },
}

function now(): Date {
  return new Date()
}

function current(token: string): LocalProgress {
  return progressStorage.update(token, (progress) => progress)
}

/** A rule broken in the phone reads like the same refusal from the server (409). */
async function local<T>(action: () => Promise<T>): Promise<T> {
  try {
    return await action()
  } catch (error) {
    if (error instanceof LocalGameError) {
      throw new ApiError(409, error.message, '/problems/invalid-state')
    }
    throw error
  }
}

const downloading = new Set<string>()

/** A game started before the package existed (or on another phone) gets it for the next time. */
function downloadInBackground(token: string): void {
  if (downloading.has(token)) {
    return
  }
  downloading.add(token)
  downloadGame(token)
    .catch(() => undefined)
    .finally(() => downloading.delete(token))
}

let flushing: Promise<void> | null = null

/**
 * Sends the queued actions in one request (POST /sessions/current/sync), one run at a time; a run that is still failing without a signal is waited for
 * and then a new one starts. Rejects without a signal or when the game is gone.
 */
async function flush(token: string): Promise<void> {
  while (flushing) {
    await flushing.catch(() => undefined)
  }
  flushing = sendQueue(token).finally(() => {
    flushing = null
  })
  return flushing
}

async function sendQueue(token: string): Promise<void> {
  const queue = progressStorage.get(token)?.queue ?? []
  if (queue.length === 0) {
    return
  }
  const sent = new Set(queue.map((event) => event.id))
  const removeSent = () =>
    progressStorage.update(token, (progress) => ({
      ...progress,
      queue: progress.queue.filter((queued) => !sent.has(queued.id)),
    }))
  try {
    await api.sync(token, queue.map(toSyncEvent))
  } catch (error) {
    // 410: received, but the code has expired meanwhile; 400: the server will never take them, do not retry forever
    if (error instanceof ApiError && (error.status === 410 || error.status === 400)) {
      removeSent()
    }
    throw error
  }
  removeSent()
}

function toSyncEvent(event: QueuedEvent): SyncEvent {
  return event.type === 'answer'
    ? { id: event.id, type: 'ANSWER', stationId: event.stationId, answer: event.answer, at: event.at }
    : { id: event.id, type: 'HINT', stationId: event.stationId, at: event.at }
}

async function flushQuietly(token: string): Promise<void> {
  await flush(token).catch(() => undefined)
}

/**
 * Sends the queue and takes over what the server knows on top: stations solved or hints revealed on another
 * phone. Returns the server state; rejects without a signal.
 */
async function synchronize(token: string): Promise<SessionState> {
  await flush(token)
  const server = await api.getSession(token)
  const progress = current(token)
  const known = new Set(progress.solved.map((solved) => solved.stationId))
  const missing = server.solvedStations.filter((station) => !known.has(station.id))

  let solved = progress.solved
  if (missing.length > 0) {
    const fresh = await api.getPackage(token)
    packageStorage.set(token, fresh)
    const solvedAt = new Date().toISOString()
    solved = [
      ...solved,
      ...missing.flatMap((station) => {
        const rawKey = fresh.unlockedKeys[station.id]
        return rawKey ? [{ stationId: station.id, rawKey, solvedAt }] : []
      }),
    ]
  }

  const hints = { ...progress.hints }
  if (server.currentStation) {
    const id = server.currentStation.id
    hints[id] = Math.max(hints[id] ?? 0, server.currentStation.revealedHints.length)
  }

  let finishedAt = progress.finishedAt
  if (server.status === 'COMPLETED' && finishedAt === null && server.finale) {
    // Finished on another phone: the end time from the server's total time
    const playedSeconds = server.finale.totalSeconds - server.penaltySeconds
    finishedAt = new Date(new Date(server.startedAt).getTime() + playedSeconds * 1000).toISOString()
  }

  progressStorage.update(token, (latest) => ({ ...latest, solved, hints, finishedAt }))
  return server
}
