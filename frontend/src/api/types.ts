// DTOs of the REST API (docs/02-architektura.md, section 3).

export type TaskType = 'TEXT' | 'NUMBER' | 'CHOICE'

export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED' | 'EXPIRED'

export interface GameSummary {
  id: string
  title: string
  subtitle: string | null
  estimatedMinutes: number | null
}

export interface GameDetail {
  id: string
  title: string
  subtitle: string | null
  introText: string
  estimatedMinutes: number | null
  stationCount: number
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
}

export interface Finale {
  outroText: string
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
