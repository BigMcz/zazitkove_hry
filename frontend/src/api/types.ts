// DTOs of the REST API (docs/02-architektura.md, section 3).

export type TaskType = 'TEXT' | 'NUMBER' | 'CHOICE'

export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED' | 'EXPIRED'

export interface GameDetail {
  id: string
  title: string
  subtitle: string | null
  introText: string
  /** Spoken intro (MP3), null = text only. */
  introAudioUrl: string | null
  /** Picture on the intro screen, null = none. */
  coverImageUrl: string | null
  estimatedMinutes: number | null
  stationCount: number
}

/** Result of checking an access code typed by the player. */
export interface CodeCheck {
  /** Printed form, e.g. ABCD-2345. */
  code: string
  game: GameDetail
  /** The code is already in use: starting with it continues the existing game. */
  resumesGame: boolean
  validUntil: string | null
}

export interface RevealedHint {
  orderNo: number
  text: string
  penaltySeconds: number
}

export interface CurrentStation {
  id: string
  orderNo: number
  title: string
  storyText: string
  /** Spoken story (MP3), null = text only. */
  storyAudioUrl: string | null
  /** The story is a listening task: its text is shown only on request. */
  listenOnly: boolean
  /** Picture to the story / task, null = none. */
  imageUrl: string | null
  imageAlt: string | null
  taskText: string
  taskType: TaskType
  choices: string[] | null
  hintsAvailable: number
  nextHintPenaltySeconds: number | null
  revealedHints: RevealedHint[]
}

export interface SolvedStation {
  id: string
  orderNo: number
  title: string
  rewardText: string
  rewardAudioUrl: string | null
  /** Where to go next, null = no direction (last station). */
  nextText: string | null
}

export interface Finale {
  outroText: string
  outroAudioUrl: string | null
  totalSeconds: number
  hintsUsed: number
}

export interface SessionState {
  /** Only in the response to POST /sessions. */
  token?: string
  status: SessionStatus
  game: { id: string; title: string }
  teamName: string | null
  startedAt: string
  penaltySeconds: number
  progress: { solved: number; total: number }
  currentStation: CurrentStation | null
  solvedStations: SolvedStation[]
  finale: Finale | null
}

export interface AnswerResult {
  correct: boolean
  message?: string
  rewardText?: string
  rewardAudioUrl?: string
  nextText?: string
  nextStationId?: string
  gameCompleted?: boolean
}

export interface HintsResult {
  revealedHints: RevealedHint[]
  hintsAvailable: number
  nextHintPenaltySeconds: number | null
  penaltySeconds: number
}

/** RFC 9457 error body. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
}
