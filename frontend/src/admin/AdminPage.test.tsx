import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createQueryClient } from '../queryClient.ts'
import AdminPage from './AdminPage.tsx'
import type { BatchSummary, MonthlyActivation } from './adminApi.ts'

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

function mockAdminApi(loggedIn: boolean) {
  let session = loggedIn ? { username: 'admin' } : { username: null }
  const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    const path = String(url)
    const method = init?.method ?? 'GET'
    if (path === '/api/v1/admin/session') return json(session)
    if (path === '/api/v1/admin/login' && method === 'POST') {
      session = { username: 'admin' }
      return json(session)
    }
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
