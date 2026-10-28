import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createQueryClient } from '../queryClient.ts'
import AdminPage from './AdminPage.tsx'
import type { AdminSession, BatchSummary, MonthlyActivation } from './adminApi.ts'

const batch: BatchSummary = {
  id: 'b1',
  gameTitle: 'Tajemství katova klíče',
  partnerName: 'Demo partner',
  quantity: 100,
  validityHours: 5,
  multiSession: false,
  note: 'Sezóna 2027 — tisk 1',
  unitPriceCzk: 150,
  createdAt: '2026-10-28T10:00:00Z',
  activatedCount: 12,
}

const reportRow: MonthlyActivation = {
  month: '2026-10',
  partnerName: 'Demo partner',
  gameTitle: 'Tajemství katova klíče',
  activatedCount: 12,
  revenueCzk: 1800,
  partnerShareCzk: 900,
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

const adminSession: AdminSession = { username: 'admin', role: 'ADMIN', partnerName: null }
const partnerSession: AdminSession = { username: 'hrad', role: 'PARTNER', partnerName: 'Demo partner' }
const anonymous: AdminSession = { username: null, role: null, partnerName: null }

const partnerUser = {
  id: 'u1',
  username: 'hrad',
  displayName: 'Pokladna Demo hradu',
  partnerId: 'p1',
  partnerName: 'Demo partner',
  enabled: true,
  createdAt: '2026-10-28T10:00:00Z',
  lastLoginAt: null,
}

function mockAdminApi(loggedIn: boolean | 'partner', loginAs = adminSession) {
  let session = loggedIn === 'partner' ? partnerSession : loggedIn ? adminSession : anonymous
  const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    const path = String(url)
    const method = init?.method ?? 'GET'
    if (path === '/api/v1/admin/session') return json(session)
    if (path === '/api/v1/admin/login' && method === 'POST') {
      session = loginAs
      return json(session)
    }
    if (path === '/api/v1/admin/logout' && method === 'POST') {
      session = anonymous
      return new Response(null, { status: 204 })
    }
    if (path === '/api/v1/admin/partners') return json([{ id: 'p1', name: 'Demo partner' }])
    if (path === '/api/v1/admin/partner-users' && method === 'GET') return json([partnerUser])
    if (path === '/api/v1/admin/partner-users' && method === 'POST') {
      return json({ ...partnerUser, id: 'u2', username: 'novy' }, 201)
    }
    if (path === '/api/v1/admin/partner-users/u1/enabled' && method === 'POST') return new Response(null, { status: 204 })
    if (path === '/api/v1/partner/batches') return json([batch])
    if (path.startsWith('/api/v1/partner/reports/monthly-activations?year=')) return json([reportRow])
    if (path === '/api/v1/partner/password' && method === 'POST') return new Response(null, { status: 204 })
    if (path === '/api/v1/admin/games') {
      return json([{ id: 'g1', title: 'Tajemství katova klíče', status: 'PUBLISHED', partnerName: 'Demo partner' }])
    }
    if (path === '/api/v1/admin/batches' && method === 'GET') return json([batch])
    if (path === '/api/v1/admin/batches' && method === 'POST') return json({ ...batch, id: 'b2' }, 201)
    if (path.startsWith('/api/v1/admin/reports/monthly-activations?year=')) return json([reportRow])
    throw new Error(`Unexpected request ${method} ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function renderAdmin() {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <AdminPage />
    </QueryClientProvider>,
  )
}

describe('AdminPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('logs in with the CSRF token from the cookie', async () => {
    const fetchMock = mockAdminApi(false)
    document.cookie = 'XSRF-TOKEN=csrf-123'
    renderAdmin()

    await userEvent.type(await screen.findByLabelText('Jméno'), 'admin')
    await userEvent.type(screen.getByLabelText('Heslo'), 'tajne')
    await userEvent.click(screen.getByRole('button', { name: 'Přihlásit' }))

    expect(await screen.findByRole('heading', { name: 'Dávky kódů' })).toBeInTheDocument()
    const login = fetchMock.mock.calls.find(([url]) => String(url) === '/api/v1/admin/login')?.[1]
    expect(login?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-123' })
    expect(String(login?.body)).toBe('username=admin&password=tajne')
  })

  it('shows the login form again after logout', async () => {
    const fetchMock = mockAdminApi(true)
    renderAdmin()

    await userEvent.click(await screen.findByRole('button', { name: 'Odhlásit' }))

    expect(await screen.findByRole('button', { name: 'Přihlásit' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Dávky kódů' })).not.toBeInTheDocument()
    const sessionCalls = fetchMock.mock.calls.filter(([url]) => String(url) === '/api/v1/admin/session')
    expect(sessionCalls.length).toBeGreaterThanOrEqual(2)
  })

  it('lists batches with a CSV download for printing', async () => {
    mockAdminApi(true)
    renderAdmin()

    const csv = await screen.findByRole('link', { name: 'CSV' })
    expect(csv).toHaveAttribute('href', '/api/v1/admin/batches/b1/codes.csv')
    const row = csv.closest('tr') as HTMLElement
    expect(within(row).getByText('Sezóna 2027 — tisk 1')).toBeInTheDocument()
    expect(within(row).getByText('5 h')).toBeInTheDocument()
  })

  it('creates a batch for the selected game', async () => {
    const fetchMock = mockAdminApi(true)
    document.cookie = 'XSRF-TOKEN=csrf-456'
    renderAdmin()

    await screen.findByRole('option', { name: /Tajemství katova klíče/ })
    const quantity = screen.getByLabelText('Počet kódů')
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '250')
    await userEvent.type(screen.getByLabelText(/Poznámka/), 'Tisk 2')
    await userEvent.click(screen.getByRole('button', { name: 'Vygenerovat kódy' }))

    expect(await screen.findByText(/Dávka 100 kódů je vytvořená/)).toBeInTheDocument()
    const create = fetchMock.mock.calls.find(
      ([url, init]) => String(url) === '/api/v1/admin/batches' && init?.method === 'POST',
    )
    expect(JSON.parse(String(create?.[1]?.body))).toEqual({
      gameId: 'g1',
      quantity: 250,
      validityHours: 5,
      note: 'Tisk 2',
      unitPriceCzk: null,
    })
    expect(create?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-456' })
  })

  it('creates and disables partner accounts', async () => {
    const fetchMock = mockAdminApi(true)
    document.cookie = 'XSRF-TOKEN=csrf-789'
    renderAdmin()

    await screen.findByRole('option', { name: 'Demo partner' })
    await userEvent.type(screen.getByLabelText('Přihlašovací jméno'), 'novy')
    await userEvent.type(screen.getByLabelText(/Počáteční heslo/), 'Silne-heslo-1')
    await userEvent.click(screen.getByRole('button', { name: 'Vytvořit účet' }))

    expect(await screen.findByText('Účet novy pro Demo partner je vytvořený.')).toBeInTheDocument()
    const create = fetchMock.mock.calls.find(
      ([url, init]) => String(url) === '/api/v1/admin/partner-users' && init?.method === 'POST',
    )
    expect(JSON.parse(String(create?.[1]?.body))).toEqual({
      partnerId: 'p1',
      username: 'novy',
      displayName: null,
      password: 'Silne-heslo-1',
    })

    const row = screen.getByText('Pokladna Demo hradu').closest('tr') as HTMLElement
    await userEvent.click(within(row).getByRole('button', { name: 'Zablokovat' }))
    const disable = fetchMock.mock.calls.find(([url]) => String(url) === '/api/v1/admin/partner-users/u1/enabled')
    expect(String(disable?.[1]?.body)).toBe('{"enabled":false}')
  })

  it('shows castle staff only their overview, without the operator tools', async () => {
    const fetchMock = mockAdminApi(false, partnerSession)
    renderAdmin()

    await userEvent.type(await screen.findByLabelText('Jméno'), 'hrad')
    await userEvent.type(screen.getByLabelText('Heslo'), 'hrad-loket')
    await userEvent.click(screen.getByRole('button', { name: 'Přihlásit' }))

    expect(await screen.findByRole('heading', { name: 'Demo partner' })).toBeInTheDocument()
    expect(await screen.findByText('říjen 2026')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Váš podíl' })).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Partner' })).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Vytištěné kódy' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'CSV' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Nová dávka kódů' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Účty hradů/ })).not.toBeInTheDocument()

    const adminCalls = fetchMock.mock.calls
      .map(([url]) => String(url))
      .filter((url) => url.startsWith('/api/v1/admin/') && !/\/(session|login)$/.test(url))
    expect(adminCalls).toEqual([])
  })

  it('lets castle staff change their password', async () => {
    const fetchMock = mockAdminApi('partner')
    document.cookie = 'XSRF-TOKEN=csrf-321'
    renderAdmin()

    await userEvent.type(await screen.findByLabelText('Současné heslo'), 'hrad-loket')
    await userEvent.type(screen.getByLabelText(/Nové heslo \(min/), 'Nove-heslo-123')
    await userEvent.type(screen.getByLabelText('Nové heslo znovu'), 'Nove-heslo-12')
    expect(screen.getByText('Nová hesla se neshodují.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Změnit heslo' })).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Nové heslo znovu'), '3')
    await userEvent.click(screen.getByRole('button', { name: 'Změnit heslo' }))

    expect(await screen.findByText('Heslo je změněné.')).toBeInTheDocument()
    const change = fetchMock.mock.calls.find(([url]) => String(url) === '/api/v1/partner/password')
    expect(JSON.parse(String(change?.[1]?.body))).toEqual({
      currentPassword: 'hrad-loket',
      newPassword: 'Nove-heslo-123',
    })
    expect(change?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-321' })
  })

  it('shows monthly activations for invoicing', async () => {
    mockAdminApi(true)
    renderAdmin()

    const month = await screen.findByText('říjen 2026')
    const row = month.closest('tr') as HTMLElement
    expect(within(row).getByText('12')).toBeInTheDocument()
    expect(within(row).getByText(/1\s800/)).toBeInTheDocument()
    expect(screen.getByText(/Celkem/)).toBeInTheDocument()
  })
})
