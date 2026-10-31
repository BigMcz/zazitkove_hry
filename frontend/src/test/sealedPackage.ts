import type { TaskType } from '../api/types.ts'
import type { GamePackage, Sealed, StationContent, Unlocked } from '../offline/types.ts'

/** A station of a test game; `answers` are already normalized, like accepted_answer.normalized_value. */
export interface TestStation {
  id: string
  title: string
  taskType: TaskType
  answers: string[]
  hints?: number[]
  letter?: string
}

const ITERATIONS = 1000

function toBase64(bytes: ArrayBuffer | Uint8Array): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
}

async function seal(key: CryptoKey, plaintext: Uint8Array<ArrayBuffer>): Promise<Sealed> {
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const data = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plaintext)
  return { iv: toBase64(iv), data: toBase64(data) }
}

function content(station: TestStation, orderNo: number): StationContent {
  return {
    id: station.id,
    orderNo,
    title: station.title,
    storyText: `Příběh ${station.title}`,
    storyAudioUrl: null,
    listenOnly: false,
    imageUrl: null,
    imageAlt: null,
    taskText: `Úkol ${station.title}`,
    taskType: station.taskType,
    choices: null,
    hints: (station.hints ?? []).map((penaltySeconds, index) => ({
      orderNo: index + 1,
      text: `Nápověda ${index + 1}`,
      penaltySeconds,
    })),
  }
}

/** Seals a test game the same way as the backend GamePackages (with few PBKDF2 iterations for speed). */
export async function sealedPackage(
  stations: TestStation[],
  session: Partial<GamePackage['session']> = {},
): Promise<GamePackage> {
  const sealedStations = []
  for (const [index, station] of stations.entries()) {
    const next = stations[index + 1]
    const unlocked: Unlocked = {
      rewardText: `Odměna ${station.title}`,
      rewardAudioUrl: null,
      nextText: next ? `Jdi na ${next.title}` : null,
      puzzleLetter: station.letter ?? null,
      next: next ? content(next, index + 2) : null,
      finale: next ? null : { outroText: 'Konec hry', outroAudioUrl: null },
    }
    const rawKey = crypto.getRandomValues(new Uint8Array(32))
    const contentKey = await crypto.subtle.importKey('raw', rawKey, 'AES-GCM', false, ['encrypt'])
    const salt = crypto.getRandomValues(new Uint8Array(16))
    const keys = []
    for (const answer of station.answers) {
      const password = await crypto.subtle.importKey('raw', new TextEncoder().encode(answer), 'PBKDF2', false, [
        'deriveKey',
      ])
      const answerKey = await crypto.subtle.deriveKey(
        { name: 'PBKDF2', hash: 'SHA-256', salt, iterations: ITERATIONS },
        password,
        { name: 'AES-GCM', length: 256 },
        false,
        ['encrypt'],
      )
      keys.push(await seal(answerKey, rawKey))
    }
    sealedStations.push({
      id: station.id,
      salt: toBase64(salt),
      keys,
      content: await seal(contentKey, new TextEncoder().encode(JSON.stringify(unlocked))),
    })
  }
  const withLetter = stations.filter((station) => station.letter)
  return {
    gameId: 'g1',
    session: {
      gameTitle: 'Testovací hra',
      teamName: null,
      startedAt: '2026-10-31T10:00:00Z',
      expiresAt: '2026-10-31T15:00:00Z',
      ...session,
    },
    kdfIterations: ITERATIONS,
    firstStation: content(stations[0], 1),
    stations: sealedStations,
    // Reversed order, so that the test sees the letters are scrambled
    puzzleSlots:
      withLetter.length === 0
        ? null
        : Object.fromEntries(withLetter.map((station, index) => [station.id, withLetter.length - 1 - index])),
    unlockedKeys: {},
    media: [],
  }
}
