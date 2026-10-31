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

/** Partner's share of the revenue valid from a day (Europe/Prague) until the next entry. */
export interface PartnerShare {
  id: string
  /** YYYY-MM-DD */
  validFrom: string
  sharePct: number
}

export interface Partner {
  id: string
  name: string
  /** IČ */
  companyId: string | null
  /** DIČ */
  vatId: string | null
  contactEmail: string | null
  contactPerson: string | null
  phone: string | null
  /** Share valid today; null when none is set yet. */
  currentSharePct: number | null
  /** All shares by validFrom, including planned ones. */
  shares: PartnerShare[]
}

/** Create or update a partner; only the name is required. */
export interface PartnerRequest {
  name: string
  companyId: string | null
  vatId: string | null
  contactEmail: string | null
  contactPerson: string | null
  phone: string | null
}

export interface SetShareRequest {
  validFrom: string
  sharePct: number
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
  partnerId: string
  partnerName: string
}

export interface BatchSummary {
  id: string
  partnerId: string
  gameTitle: string
  partnerName: string
  quantity: number
  /** null = codes never expire (development batch) */
  validityHours: number | null
  multiSession: boolean
  note: string | null
  unitPriceCzk: number | null
  createdAt: string
  /** null = still in production, the codes do not work yet */
  handedOverAt: string | null
  activatedCount: number
  /** Cards not used yet — for a handed-over batch the cards the partner should still have. */
  notActivatedCount: number
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
  partnerId: string
  partnerName: string
  gameTitle: string
  activatedCount: number
  revenueCzk: number | null
  partnerShareCzk: number | null
}

/** Key figures of a year; development batches are left out. */
export interface Summary {
  year: number
  activatedThisMonth: number
  activatedInYear: number
  revenueCzk: number | null
  partnerShareCzk: number | null
  /** Revenue minus the partners' share; only for the operator. */
  profitCzk?: number | null
  /** This month (Europe/Prague) regardless of the chosen year. */
  revenueThisMonthCzk: number | null
  partnerShareThisMonthCzk: number | null
  /** Only for the operator. */
  profitThisMonthCzk?: number | null
  /** Cards the castle(s) should still have: from the castle's latest stock count when there is one. */
  cardsInStock: number
  /** Handed over and not activated, regardless of the stock counts. */
  cardsInStockComputed: number
  /** Latest stock count of the castle; null without one or for all castles. */
  stockCountedAt: string | null
  /** Generated, not handed over yet. */
  cardsInProduction: number
  /** All cards ever handed over. */
  cardsDelivered: number
  /** Stock of each game of the castle; empty for all castles. */
  games: GameStock[]
}

/** Cards of one game at one castle; development batches are left out. */
export interface GameStock {
  gameId: string
  gameTitle: string
  cardsDelivered: number
  /** From the castle's latest stock count of the game when there is one. */
  cardsInStock: number
  /** Handed over and not activated, regardless of the stock counts. */
  cardsInStockComputed: number
  stockCountedAt: string | null
  cardsInProduction: number
  activatedLast28Days: number
  /** How many days the cards in stock last at that pace; null without sales in that time. */
  stockDays: number | null
}

/** One castle in the comparison of all castles for a year; development batches are left out. */
export interface PartnerStats {
  partnerId: string
  partnerName: string
  activatedInYear: number
  revenueCzk: number | null
  partnerShareCzk: number | null
  profitCzk: number | null
  cardsDelivered: number
  /** Cards the castle should still have: from its latest stock count when there is one. */
  cardsInStock: number
  /** Handed over and not activated, regardless of the stock counts. */
  cardsInStockComputed: number
  stockCountedAt: string | null
  cardsInProduction: number
  /** Pace of selling for the stock estimate, regardless of the chosen year. */
  activatedLast28Days: number
  /** The game whose cards run out first: days its cards last; null without sales in that time. */
  stockDays: number | null
  games: GameStock[]
}

/** One station in the games of a year. */
export interface StationStats {
  stationId: string
  orderNo: number
  title: string
  /** Games that got to the station. */
  reached: number
  solved: number
  /** Left or expired while the station was not solved yet. */
  stoppedHere: number
  /** From reaching to solving; null when nobody solved it. */
  medianSeconds: number | null
  wrongAttempts: number
  teamsWithHint: number
}

/** Games started in a year with sold cards; durations only of completed games. */
export interface GameStats {
  gameId: string
  gameTitle: string
  partnerName: string
  started: number
  completed: number
  /** Left by the players or expired before the end. */
  unfinished: number
  /** Still running and valid. */
  playing: number
  durationP25Seconds: number | null
  durationMedianSeconds: number | null
  durationP75Seconds: number | null
  /** Including the penalties for hints, i.e. the final time. */
  durationWithPenaltyMedianSeconds: number | null
  stations: StationStats[]
}

function csrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {}
}

const withPartner = (path: string, partnerId?: string) =>
  partnerId ? `${path}${path.includes('?') ? '&' : '?'}partnerId=${encodeURIComponent(partnerId)}` : path

const partnerPath = (partnerId: string) => `/admin/partners/${encodeURIComponent(partnerId)}`

const batchPath = (batchId: string) => `/admin/batches/${encodeURIComponent(batchId)}`

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

  /** Key figures of all partners, or of one partner. */
  dashboard: (year: number, partnerId?: string) =>
    apiRequest<Summary>(withPartner(`/admin/dashboard?year=${year}`, partnerId)),

  games: () => apiRequest<AdminGame[]>('/admin/games'),

  batches: (partnerId?: string) => apiRequest<BatchSummary[]>(withPartner('/admin/batches', partnerId)),

  createBatch: (request: CreateBatchRequest) =>
    apiRequest<BatchSummary>('/admin/batches', { method: 'POST', body: request, headers: csrfHeader() }),

  /** The printed cards were handed over to the partner — from now on the codes work. */
  handOver: (batchId: string) =>
    apiRequest<BatchSummary>(`${batchPath(batchId)}/handover`, { method: 'POST', headers: csrfHeader() }),

  undoHandOver: (batchId: string) =>
    apiRequest<BatchSummary>(`${batchPath(batchId)}/handover`, { method: 'DELETE', headers: csrfHeader() }),

  codesCsvUrl: (batchId: string) => `/api/v1${batchPath(batchId)}/codes.csv`,

  monthlyActivations: (year: number, partnerId?: string) =>
    apiRequest<MonthlyActivation[]>(withPartner(`/admin/reports/monthly-activations?year=${year}`, partnerId)),

  /** All castles side by side, with how long their cards last. */
  partnerStats: (year: number) => apiRequest<PartnerStats[]>(`/admin/reports/partners?year=${year}`),

  /** How the games are played; only for the operator. */
  gameplay: (year: number, partnerId?: string) =>
    apiRequest<GameStats[]>(withPartner(`/admin/reports/gameplay?year=${year}`, partnerId)),

  partners: () => apiRequest<Partner[]>('/admin/partners'),

  partner: (partnerId: string) => apiRequest<Partner>(partnerPath(partnerId)),

  createPartner: (request: PartnerRequest) =>
    apiRequest<Partner>('/admin/partners', { method: 'POST', body: request, headers: csrfHeader() }),

  updatePartner: (partnerId: string, request: PartnerRequest) =>
    apiRequest<Partner>(partnerPath(partnerId), { method: 'PUT', body: request, headers: csrfHeader() }),

  /** A share for an existing date replaces it. */
  setPartnerShare: (partnerId: string, request: SetShareRequest) =>
    apiRequest<Partner>(`${partnerPath(partnerId)}/shares`, { method: 'POST', body: request, headers: csrfHeader() }),

  deletePartnerShare: (partnerId: string, shareId: string) =>
    apiRequest<Partner>(`${partnerPath(partnerId)}/shares/${encodeURIComponent(shareId)}`, {
      method: 'DELETE',
      headers: csrfHeader(),
    }),

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
  summary: (year: number) => apiRequest<Summary>(`/partner/summary?year=${year}`),

  batches: () => apiRequest<BatchSummary[]>('/partner/batches'),

  shares: () => apiRequest<PartnerShare[]>('/partner/shares'),

  /** The castle's own count of the cards in stock per game, each at most its cardsInStockComputed. */
  stockCount: (year: number, games: { gameId: string; cardsInStock: number }[]) =>
    apiRequest<Summary>(`/partner/stock-count?year=${year}`, {
      method: 'POST',
      body: { games },
      headers: csrfHeader(),
    }),

  monthlyActivations: (year: number) =>
    apiRequest<MonthlyActivation[]>(`/partner/reports/monthly-activations?year=${year}`),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>('/partner/password', {
      method: 'POST',
      body: { currentPassword, newPassword },
      headers: csrfHeader(),
    }),
}
