import { apiRequest } from '../api/client.ts'

// Administration API (docs/02-architektura.md). Uses the session cookie of the admin login and
// Spring Security's CSRF protection: the token comes in the XSRF-TOKEN cookie and every POST
// sends it back in the X-XSRF-TOKEN header.

export type GameStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface AdminSession {
  username: string | null
}

export interface AdminGame {
  id: string
  title: string
  status: GameStatus
  partnerName: string
}

export interface BatchSummary {
  id: string
  gameTitle: string
  partnerName: string
  quantity: number
  /** null = codes never expire (development batch) */
  validityHours: number | null
  multiSession: boolean
  note: string | null
  unitPriceCzk: number | null
  createdAt: string
  activatedCount: number
}

export interface CreateBatchRequest {
  gameId: string
  quantity: number
  validityHours: number
  note: string | null
  unitPriceCzk: number | null
}

export interface MonthlyActivation {
  /** YYYY-MM in Europe/Prague */
  month: string
  partnerName: string
  gameTitle: string
  activatedCount: number
  revenueCzk: number | null
  partnerShareCzk: number | null
}

function csrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {}
}

export const adminApi = {
  /** Also issues the XSRF-TOKEN cookie needed for the login. */
  session: () => apiRequest<AdminSession>('/admin/session'),

  login: (username: string, password: string) =>
    apiRequest<AdminSession>('/admin/login', {
      method: 'POST',
      body: new URLSearchParams({ username, password }),
      headers: csrfHeader(),
    }),

  logout: () => apiRequest<void>('/admin/logout', { method: 'POST', headers: csrfHeader() }),

  games: () => apiRequest<AdminGame[]>('/admin/games'),

  batches: () => apiRequest<BatchSummary[]>('/admin/batches'),

  createBatch: (request: CreateBatchRequest) =>
    apiRequest<BatchSummary>('/admin/batches', { method: 'POST', body: request, headers: csrfHeader() }),

  monthlyActivations: (year: number) =>
    apiRequest<MonthlyActivation[]>(`/admin/reports/monthly-activations?year=${year}`),

  codesCsvUrl: (batchId: string) => `/api/v1/admin/batches/${encodeURIComponent(batchId)}/codes.csv`,
}
