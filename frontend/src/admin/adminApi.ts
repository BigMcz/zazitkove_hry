import { apiRequest } from '../api/client.ts'

// Administration API (docs/02-architektura.md). Two roles share one login: ADMIN (operator,
// /api/v1/admin/**) and PARTNER (castle staff, /api/v1/partner/**, only their own data).
// Uses the session cookie of the login and Spring Security's CSRF protection: the token comes
// in the XSRF-TOKEN cookie and every POST sends it back in the X-XSRF-TOKEN header.

export type GameStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export type AdminRole = 'ADMIN' | 'PARTNER'

/** username null = nobody is logged in */
export interface AdminSession {
  username: string | null
  role: AdminRole | null
  /** only for PARTNER */
  partnerName: string | null
}

export interface PartnerOption {
  id: string
  name: string
}

export interface PartnerUser {
  id: string
  username: string
  displayName: string | null
  partnerId: string
  partnerName: string
  enabled: boolean
  createdAt: string
  lastLoginAt: string | null
}

export interface CreatePartnerUserRequest {
  partnerId: string
  username: string
  displayName: string | null
  password: string
}

/** Same rule as the backend (PartnerUserDtos.MIN_PASSWORD_LENGTH). */
export const MIN_PASSWORD_LENGTH = 10

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

  partners: () => apiRequest<PartnerOption[]>('/admin/partners'),

  partnerUsers: () => apiRequest<PartnerUser[]>('/admin/partner-users'),

  createPartnerUser: (request: CreatePartnerUserRequest) =>
    apiRequest<PartnerUser>('/admin/partner-users', { method: 'POST', body: request, headers: csrfHeader() }),

  setPartnerUserPassword: (userId: string, password: string) =>
    apiRequest<void>(`/admin/partner-users/${encodeURIComponent(userId)}/password`, {
      method: 'POST',
      body: { password },
      headers: csrfHeader(),
    }),

  setPartnerUserEnabled: (userId: string, enabled: boolean) =>
    apiRequest<void>(`/admin/partner-users/${encodeURIComponent(userId)}/enabled`, {
      method: 'POST',
      body: { enabled },
      headers: csrfHeader(),
    }),
}

/** API for castle staff; the server limits everything to the partner of the logged-in account. */
export const partnerApi = {
  batches: () => apiRequest<BatchSummary[]>('/partner/batches'),

  monthlyActivations: (year: number) =>
    apiRequest<MonthlyActivation[]>(`/partner/reports/monthly-activations?year=${year}`),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>('/partner/password', {
      method: 'POST',
      body: { currentPassword, newPassword },
      headers: csrfHeader(),
    }),
}
