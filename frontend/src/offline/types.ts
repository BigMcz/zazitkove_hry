import type { TaskType } from '../api/types.ts'

/**
 * Offline game package of a session (GET /sessions/current/package, backend SessionPackage). Has no accepted
 * answers: what a station reveals is encrypted by a key that only a correct answer opens.
 */
export interface GamePackage {
  gameId: string
  /** PBKDF2-HMAC-SHA256 iterations for the answer keys. */
  kdfIterations: number
  firstStation: StationContent
  stations: SealedStation[]
  /** Slot of the letter of each station that has one (in station order), null when the game has no puzzle. */
  puzzleSlots: number[] | null
  /** Content keys (base64) of the stations this session already solved. */
  unlockedKeys: Record<string, string>
  /** Pictures and sounds of the game to download for playing without a signal. */
  media: string[]
}

export interface StationContent {
  id: string
  orderNo: number
  title: string
  storyText: string
  storyAudioUrl: string | null
  listenOnly: boolean
  imageUrl: string | null
  imageAlt: string | null
  taskText: string
  taskType: TaskType
  choices: string[] | null
  hints: { orderNo: number; text: string; penaltySeconds: number }[]
}

/** AES-GCM: base64 IV and base64 ciphertext with the tag. */
export interface Sealed {
  iv: string
  data: string
}

/** `keys`: the content key encrypted by the key of each accepted answer; `content`: {@link Unlocked}. */
export interface SealedStation {
  id: string
  salt: string
  keys: Sealed[]
  content: Sealed
}

/** What solving a station reveals; `next` is null for the last station, which has `finale`. */
export interface Unlocked {
  rewardText: string
  rewardAudioUrl: string | null
  nextText: string | null
  puzzleLetter: string | null
  next: StationContent | null
  finale: { outroText: string; outroAudioUrl: string | null } | null
}
