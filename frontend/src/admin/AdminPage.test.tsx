import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createQueryClient } from '../queryClient.ts'
import AdminPage from './AdminPage.tsx'
import type { AdminSession, BatchSummary, MonthlyActivation, Partner, Summary } from './adminApi.ts'
import { firstOfNextMonth, todayInPrague } from './format.ts'

const adminSession: AdminSession = { username: 'admin', role: 'ADMIN', partnerName: null }
const partnerSession: AdminSession = { username: 'hrad', role: 'PARTNER', partnerName: 'Demo partner' }
const anonymous: AdminSession = { username: null, role: null, partnerName: null }

const demoPartner: Partner = {
  id: 'p1',
  name: 'Demo partner',
  companyId: '12345678',
  vatId: 'CZ12345678',
  contactEmail: 'demo@example.cz',
  contactPerson: 'Jan Kastelán',
  phone: '+420 123 456 789',
  currentSharePct: 25,
  shares: [
    { id: 's1', validFrom: '2026-01-01', sharePct: 25 },
    { id: 's2', validFrom: '2099-01-01', sharePct: 30 },
  ],
}

const deliveredBatch: BatchSummary = {
  id: 'b1',
  partnerId: 'p1',
  gameTitle: 'Tajemství katova klíče',
  partnerName: 'Demo partner',
  quantity: 100,
  validityHours: 5,
  multiSession: false,
  note: 'Sezóna 2027 — tisk 1',
  unitPriceCzk: 150,
  createdAt: '2026-10-01T10:00:00Z',
  handedOverAt: '2026-10-05T10:00:00Z',
  activatedCount: 12,
  notActivatedCount: 88,
}

const batchInProduction: BatchSummary = {
  ...deliveredBatch,
  id: 'b2',
  note: 'Tisk 2',
  createdAt: '2026-10-20T10:00:00Z',
  handedOverAt: null,
  activatedCount: 0,
  notActivatedCount: 50,
  quantity: 50,
}

const summary: Summary = {
  year: 2026,
  activatedThisMonth: 4,
  activatedInYear: 12,
  revenueCzk: 1800,
  partnerShareCzk: 450,
  profitCzk: 1350,
  cardsInStock: 88,
  cardsInProduction: 50,
  cardsDelivered: 100,
}

const reportRow: MonthlyActivation = {
  month: '2026-10',
  partnerName: 'Demo partner',
  gameTitle: 'Tajemství katova klíče',
  activatedCount: 12,
  revenueCzk: 1800,
  partnerShareCzk: 450,
}

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

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function mockAdminApi(loggedIn: boolean | 'partner', loginAs = adminSession) {
  let session = loggedIn === 'partner' ? partnerSession : loggedIn ? adminSession : anonymous
  const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    const path = String(url)
    const method = init?.method ?? 'GET'
    const body = () => JSON.parse(String(init?.body))
    if (path === '/api/v1/admin/session') return json(session)
    if (path === '/api/v1/admin/login' && method === 'POST') {
      session = loginAs
      return json(session)
    }
    if (path === '/api/v1/admin/logout' && method === 'POST') {
      session = anonymous
      return new Response(null, { status: 204 })
    }
    if (path.startsWith('/api/v1/admin/dashboard?year=')) return json(summary)
    if (path === '/api/v1/admin/partners' && method === 'GET') return json([demoPartner])
    if (path === '/api/v1/admin/partners' && method === 'POST') {
      return json({ ...demoPartner, ...body(), id: 'p2', shares: [] }, 201)
    }
    if (path === '/api/v1/admin/partners/p1' && method === 'GET') return json(demoPartner)
    if (path === '/api/v1/admin/partners/p1' && method === 'PUT') return json({ ...demoPartner, ...body() })
    if (path === '/api/v1/admin/partners/p1/shares' && method === 'POST') return json(demoPartner)
    if (path === '/api/v1/admin/partners/p1/shares/s2' && method === 'DELETE') return json(demoPartner)
    if (path === '/api/v1/admin/partner-users' && method === 'GET') {
      return json([partnerUser, { ...partnerUser, id: 'u9', username: 'jiny', partnerId: 'p9', partnerName: 'Jiný hrad' }])
    }
    if (path === '/api/v1/admin/partner-users' && method === 'POST') {
      return json({ ...partnerUser, id: 'u2', username: 'novy' }, 201)
    }
    if (path === '/api/v1/admin/games') {
      return json([
        { id: 'g1', title: 'Tajemství katova klíče', status: 'PUBLISHED', partnerId: 'p1', partnerName: 'Demo partner' },
        { id: 'g9', title: 'Cizí hra', status: 'PUBLISHED', partnerId: 'p9', partnerName: 'Jiný hrad' },
      ])
    }
    if (path === '/api/v1/admin/batches?partnerId=p1') return json([batchInProduction, deliveredBatch])
    if (path === '/api/v1/admin/batches' && method === 'POST') return json(batchInProduction, 201)
    if (path === '/api/v1/admin/batches/b2/handover' && method === 'POST') {
      return json({ ...batchInProduction, handedOverAt: '2026-10-28T10:00:00Z' })
    }
    if (path.startsWith('/api/v1/admin/reports/monthly-activations?year=')) return json([reportRow])
    if (path.startsWith('/api/v1/partner/summary?year=')) return json({ ...summary, profitCzk: undefined })
    if (path === '/api/v1/partner/batches') return json([deliveredBatch])
    if (path === '/api/v1/partner/shares') return json(demoPartner.shares)
    if (path.startsWith('/api/v1/partner/reports/monthly-activations?year=')) return json([reportRow])
    if (path === '/api/v1/partner/password' && method === 'POST') return new Response(null, { status: 204 })
    throw new Error(`Unexpected request ${method} ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function CurrentUrl() {
  const location = useLocation()
  return <output data-testid="url">{location.pathname + location.search}</output>
}

function renderAdmin(url = '/admin') {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <MemoryRouter initialEntries={[url]}>
        <Routes>
          <Route path="/admin/*" element={<AdminPage />} />
        </Routes>
        <CurrentUrl />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const calls = (fetchMock: ReturnType<typeof mockAdminApi>, path: string, method = 'GET') =>
  fetchMock.mock.calls.filter(([url, init]) => String(url) === path && (init?.method ?? 'GET') === method)

describe('AdminPage — operator', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('logs in with the CSRF token from the cookie and shows the dashboard', async () => {
    const fetchMock = mockAdminApi(false)
    document.cookie = 'XSRF-TOKEN=csrf-123'
    renderAdmin()

    await userEvent.type(await screen.findByLabelText('Jméno'), 'admin')
    await userEvent.type(screen.getByLabelText('Heslo'), 'tajne')
    await userEvent.click(screen.getByRole('button', { name: 'Přihlásit' }))

    expect(await screen.findByRole('heading', { name: 'Všechny hrady' })).toBeInTheDocument()
    const login = calls(fetchMock, '/api/v1/admin/login', 'POST')[0]?.[1]
    expect(login?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-123' })
    expect(String(login?.body)).toBe('username=admin&password=tajne')
  })

  it('shows key figures of all castles with the profit as cards', async () => {
    mockAdminApi(true)
    renderAdmin()

    const profit = (await screen.findByText(/^Zisk \d{4}$/)).parentElement as HTMLElement
    expect(await within(profit).findByText(/1\s350/)).toBeInTheDocument()
    expect(screen.getByText('Kartiček u hradů').parentElement).toHaveTextContent('88')
    expect(screen.getByText('Ve výrobě').parentElement).toHaveTextContent('50')
  })

  it('lists castles in the left menu and opens one', async () => {
    mockAdminApi(true)
    renderAdmin()

    const nav = await screen.findByRole('navigation', { name: 'Hrady' })
    await userEvent.click(await within(nav).findByRole('link', { name: /Demo partner/ }))

    expect(await screen.findByRole('heading', { name: 'Demo partner', level: 2 })).toBeInTheDocument()
    expect(screen.getByText(/IČ 12345678 · Jan Kastelán/)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Přehled', selected: true })).toBeInTheDocument()
    expect(screen.getByTestId('url')).toHaveTextContent('/admin/partneri/p1')
  })

  it('adds a partner with company details and continues to its share', async () => {
    const fetchMock = mockAdminApi(true)
    document.cookie = 'XSRF-TOKEN=csrf-1'
    renderAdmin()

    await userEvent.type(await screen.findByLabelText('Název firmy'), 'Hrad Loket')
    await userEvent.type(screen.getByLabelText('IČ'), '87654321')
    await userEvent.type(screen.getByLabelText('DIČ'), 'CZ87654321')
    await userEvent.type(screen.getByLabelText('Kontaktní osoba'), 'Jana Nováková')
    await userEvent.type(screen.getByLabelText('E-mail'), 'pokladna@hrad.cz')
    await userEvent.type(screen.getByLabelText('Telefon'), '+420 352 111 222')
    await userEvent.click(screen.getByRole('button', { name: 'Přidat partnera' }))

    expect(await screen.findByTestId('url')).toHaveTextContent('/admin/partneri/p2?sekce=podily')
    const create = calls(fetchMock, '/api/v1/admin/partners', 'POST')[0]?.[1]
    expect(JSON.parse(String(create?.body))).toEqual({
      name: 'Hrad Loket',
      companyId: '87654321',
      vatId: 'CZ87654321',
      contactEmail: 'pokladna@hrad.cz',
      contactPerson: 'Jana Nováková',
      phone: '+420 352 111 222',
    })
    expect(create?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-1' })
  })

  it('manages accounts of all castles on the dashboard', async () => {
    const fetchMock = mockAdminApi(true)
    renderAdmin()

    const heading = await screen.findByRole('heading', { name: 'Přístupy hradů do administrace' })
    const section = heading.closest('section') as HTMLElement
    expect(await within(section).findByText('jiny')).toBeInTheDocument()
    await within(section).findByRole('option', { name: 'Demo partner' })
    await userEvent.type(within(section).getByLabelText('Přihlašovací jméno'), 'novy')
    await userEvent.type(within(section).getByLabelText(/Počáteční heslo/), 'Silne-heslo-1')
    await userEvent.click(within(section).getByRole('button', { name: 'Vytvořit účet' }))

    expect(await within(section).findByText(/Účet novy pro Demo partner/)).toBeInTheDocument()
    const create = calls(fetchMock, '/api/v1/admin/partner-users', 'POST')[0]?.[1]
    expect(JSON.parse(String(create?.body))).toEqual({
      partnerId: 'p1',
      username: 'novy',
      displayName: null,
      password: 'Silne-heslo-1',
    })
  })

  it('shows only the partner’s data on its overview tab', async () => {
    const fetchMock = mockAdminApi(true)
    renderAdmin('/admin/partneri/p1')

    expect(await screen.findByText('říjen 2026')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Partner' })).not.toBeInTheDocument()
    const requested = fetchMock.mock.calls.map(([url]) => String(url))
    expect(requested.some((url) => url.startsWith('/api/v1/admin/dashboard?') && url.endsWith('partnerId=p1'))).toBe(true)
    expect(requested.some((url) => url.includes('monthly-activations') && url.endsWith('partnerId=p1'))).toBe(true)
  })

  it('generates codes for the partner’s games and marks a printed batch as handed over', async () => {
    const fetchMock = mockAdminApi(true)
    renderAdmin('/admin/partneri/p1?sekce=kody')

    expect(await screen.findByRole('option', { name: 'Tajemství katova klíče' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Cizí hra' })).not.toBeInTheDocument()

    const inProduction = (await screen.findByText('Tisk 2')).closest('tr') as HTMLElement
    expect(within(inProduction).getByText('Ve výrobě')).toBeInTheDocument()
    const delivered = screen.getByText('Sezóna 2027 — tisk 1').closest('tr') as HTMLElement
    expect(within(delivered).getByText(/^Předáno/)).toBeInTheDocument()
    expect(within(delivered).getByText('88')).toBeInTheDocument()
    expect(within(delivered).queryByRole('button', { name: 'Vrátit předání' })).not.toBeInTheDocument()
    expect(within(delivered).getByRole('link', { name: 'CSV pro tisk' })).toHaveAttribute(
      'href',
      '/api/v1/admin/batches/b1/codes.csv',
    )

    await userEvent.click(within(inProduction).getByRole('button', { name: 'Označit jako předané' }))
    expect(calls(fetchMock, '/api/v1/admin/batches/b2/handover', 'POST')).toHaveLength(1)
  })

  it('creates a batch for the selected partner game', async () => {
    const fetchMock = mockAdminApi(true)
    renderAdmin('/admin/partneri/p1?sekce=kody')

    await screen.findByRole('option', { name: 'Tajemství katova klíče' })
    const quantity = screen.getByLabelText('Počet kódů')
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '250')
    await userEvent.click(screen.getByRole('button', { name: 'Vygenerovat kódy' }))

    expect(await screen.findByText(/je vytvořená \(ve výrobě\)/)).toBeInTheDocument()
    expect(JSON.parse(String(calls(fetchMock, '/api/v1/admin/batches', 'POST')[0]?.[1]?.body))).toEqual({
      gameId: 'g1',
      quantity: 250,
      validityHours: 5,
      note: null,
      unitPriceCzk: null,
    })
  })

  it('plans a share from the first of next month and warns about past dates', async () => {
    const fetchMock = mockAdminApi(true)
    renderAdmin('/admin/partneri/p1?sekce=podily')

    expect(await screen.findByText('platí dnes')).toBeInTheDocument()
    expect(screen.getByText('naplánováno')).toBeInTheDocument()
    expect(screen.getByLabelText('Platí od')).toHaveValue(firstOfNextMonth(todayInPrague()))
    await userEvent.type(screen.getByLabelText('Podíl (%)'), '30')
    await userEvent.click(screen.getByRole('button', { name: 'Přidat podíl' }))
    expect(JSON.parse(String(calls(fetchMock, '/api/v1/admin/partners/p1/shares', 'POST')[0]?.[1]?.body))).toEqual({
      validFrom: firstOfNextMonth(todayInPrague()),
      sharePct: 30,
    })

    const date = screen.getByLabelText('Platí od')
    await userEvent.clear(date)
    await userEvent.type(date, '2026-01-01')
    expect(screen.getByRole('note')).toHaveTextContent('už mohly být vyfakturované')
    expect(screen.getByRole('button', { name: 'Nahradit podíl' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Smazat podíl od 1. 1. 2099' }))
    expect(calls(fetchMock, '/api/v1/admin/partners/p1/shares/s2', 'DELETE')).toHaveLength(1)
  })

  it('edits company details', async () => {
    const fetchMock = mockAdminApi(true)
    renderAdmin('/admin/partneri/p1?sekce=udaje')

    const phone = await screen.findByLabelText('Telefon')
    expect(phone).toHaveValue('+420 123 456 789')
    await userEvent.clear(phone)
    await userEvent.click(screen.getByRole('button', { name: 'Uložit údaje' }))

    expect(await screen.findByText('Údaje jsou uložené.')).toBeInTheDocument()
    expect(JSON.parse(String(calls(fetchMock, '/api/v1/admin/partners/p1', 'PUT')[0]?.[1]?.body))).toMatchObject({
      name: 'Demo partner',
      companyId: '12345678',
      phone: null,
    })
  })

  it('shows and creates only the partner’s own accounts on its tab', async () => {
    const fetchMock = mockAdminApi(true)
    renderAdmin('/admin/partneri/p1?sekce=ucty')

    expect(await screen.findByText('Pokladna Demo hradu')).toBeInTheDocument()
    expect(screen.queryByText('jiny')).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Partner' })).not.toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Přihlašovací jméno'), 'novy')
    await userEvent.type(screen.getByLabelText(/Počáteční heslo/), 'Silne-heslo-1')
    await userEvent.click(screen.getByRole('button', { name: 'Vytvořit účet' }))
    expect(await screen.findByText(/Účet novy pro Demo partner/)).toBeInTheDocument()
    expect(JSON.parse(String(calls(fetchMock, '/api/v1/admin/partner-users', 'POST')[0]?.[1]?.body))).toMatchObject({
      partnerId: 'p1',
    })
  })

  it('shows the login form again after logout', async () => {
    mockAdminApi(true)
    renderAdmin()

    await userEvent.click(await screen.findByRole('button', { name: 'Odhlásit' }))

    expect(await screen.findByRole('button', { name: 'Přihlásit' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Hrady' })).not.toBeInTheDocument()
  })
})

describe('AdminPage — castle', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('shows the castle its stock of cards, deliveries and months — and nothing of the operator', async () => {
    const fetchMock = mockAdminApi(false, partnerSession)
    renderAdmin()

    await userEvent.type(await screen.findByLabelText('Jméno'), 'hrad')
    await userEvent.type(screen.getByLabelText('Heslo'), 'hrad-loket')
    await userEvent.click(screen.getByRole('button', { name: 'Přihlásit' }))

    expect(await screen.findByRole('heading', { name: 'Demo partner', level: 1 })).toBeInTheDocument()
    const stock = (await screen.findByText('Kartiček na skladě')).parentElement as HTMLElement
    expect(await within(stock).findByText('88')).toBeInTheDocument()
    expect(screen.queryByText(/^Zisk/)).not.toBeInTheDocument()
    const delivery = (await screen.findByText('Sezóna 2027 — tisk 1')).closest('tr') as HTMLElement
    expect(within(delivery).getByText('88')).toBeInTheDocument()
    expect(within(delivery).queryByRole('link', { name: 'CSV pro tisk' })).not.toBeInTheDocument()
    expect(await screen.findByText(/Váš podíl z tržby:/)).toHaveTextContent('25 % (od 1. 1. 2026)')
    expect(screen.queryByRole('navigation', { name: 'Hrady' })).not.toBeInTheDocument()

    const adminCalls = fetchMock.mock.calls
      .map(([url]) => String(url))
      .filter((url) => url.startsWith('/api/v1/admin/') && !/\/(session|login)$/.test(url))
    expect(adminCalls).toEqual([])
  })

  it('changes the password from the key icon in the header', async () => {
    const fetchMock = mockAdminApi('partner')
    document.cookie = 'XSRF-TOKEN=csrf-321'
    renderAdmin()

    await userEvent.click(await screen.findByRole('button', { name: 'Změnit heslo' }))
    const dialog = screen.getByRole('dialog', { name: 'Změna hesla' })
    await userEvent.type(within(dialog).getByLabelText('Současné heslo'), 'hrad-loket')
    await userEvent.type(within(dialog).getByLabelText(/Nové heslo \(min/), 'Nove-heslo-123')
    await userEvent.type(within(dialog).getByLabelText('Nové heslo znovu'), 'Nove-heslo-12')
    expect(within(dialog).getByText('Nová hesla se neshodují.')).toBeInTheDocument()

    await userEvent.type(within(dialog).getByLabelText('Nové heslo znovu'), '3')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Změnit heslo' }))

    expect(await within(dialog).findByText('Heslo je změněné.')).toBeInTheDocument()
    const change = calls(fetchMock, '/api/v1/partner/password', 'POST')[0]?.[1]
    expect(JSON.parse(String(change?.body))).toEqual({ currentPassword: 'hrad-loket', newPassword: 'Nove-heslo-123' })
    expect(change?.headers).toMatchObject({ 'X-XSRF-TOKEN': 'csrf-321' })

    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
