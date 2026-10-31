import type {
  AnswerResult,
  CodeCheck,
  HintsResult,
  ProblemDetail,
  SessionState,
} from './types.ts'
import type { GamePackage, SyncEvent } from '../offline/types.ts'

const BASE_URL = '/api/v1'
const TOKEN_HEADER = 'X-Session-Token'

/** Failed API call. `status` 0 means the server could not be reached at all. */
export class ApiError extends Error {
  readonly status: number
  readonly type: string | undefined

  constructor(status: number, message: string, type?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.type = type
  }
}

/** The session no longer exists for this device (unknown token or expired game). */
export function isSessionGone(error: unknown): error is ApiError {
  return error instanceof ApiError && (error.status === 401 || error.status === 410)
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** JSON body, or URLSearchParams sent as a form (admin login). */
  body?: unknown
  token?: string | null
  headers?: Record<string, string>
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  let body: BodyInit | undefined
  if (options.body instanceof URLSearchParams) {
    body = options.body
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }
  if (options.token) {
    headers[TOKEN_HEADER] = options.token
  }

  let response: Response
  try {
    response = await fetch(BASE_URL + path, { method: options.method ?? 'GET', headers, body })
  } catch {
    throw new ApiError(0, 'Nepodařilo se spojit se serverem. Zkontroluj připojení k internetu.')
  }

  if (!response.ok) {
    let problem: ProblemDetail = {}
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      // not a JSON body (e.g. a proxy error page)
    }
    throw new ApiError(
      response.status,
      problem.detail ?? 'Něco se pokazilo. Zkus to prosím znovu.',
      problem.type,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

const station = (stationId: string) => `/sessions/current/stations/${encodeURIComponent(stationId)}`

export const api = {
  checkCode: (code: string) => apiRequest<CodeCheck>('/access-codes/check', { method: 'POST', body: { code } }),

  startSession: (code: string, teamName: string | null) =>
    apiRequest<SessionState>('/sessions', { method: 'POST', body: { code, teamName } }),

  getSession: (token: string) => apiRequest<SessionState>('/sessions/current', { token }),

  getPackage: (token: string) => apiRequest<GamePackage>('/sessions/current/package', { token }),

  /** Actions played in the phone without a signal, in order; the server applies each only once. */
  sync: (token: string, events: SyncEvent[]) =>
    apiRequest<SessionState>('/sessions/current/sync', { method: 'POST', token, body: { events } }),

  submitAnswer: (token: string, stationId: string, answer: string) =>
    apiRequest<AnswerResult>(`${station(stationId)}/answers`, { method: 'POST', token, body: { answer } }),

  revealHint: (token: string, stationId: string) =>
    apiRequest<HintsResult>(`${station(stationId)}/hints`, { method: 'POST', token }),

  abandon: (token: string) => apiRequest<void>('/sessions/current/abandon', { method: 'POST', token }),
}
