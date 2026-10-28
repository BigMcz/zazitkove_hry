import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../api/client.ts'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { adminApi, type CreateBatchRequest } from './adminApi.ts'
import { formatDateTime, formatMoney, formatMonth, formatNumber } from './format.ts'

const adminKeys = {
  session: ['admin', 'session'] as const,
  games: ['admin', 'games'] as const,
  batches: ['admin', 'batches'] as const,
  report: (year: number) => ['admin', 'report', year] as const,
}

const thisYear = Number(new Intl.DateTimeFormat('en', { year: 'numeric', timeZone: 'Europe/Prague' }).format(new Date()))

/** Administration for developers and the operator: batches of access codes and monthly activations. */
export default function AdminPage() {
  const session = useQuery({ queryKey: adminKeys.session, queryFn: adminApi.session })

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-8 px-4 pt-6 pb-12">
      {session.isPending && <p role="status">Načítám…</p>}
      {session.isError && <ErrorBanner message={session.error.message} onRetry={() => void session.refetch()} />}
      {session.data && (session.data.username ? <Dashboard username={session.data.username} /> : <LoginForm />)}
    </main>
  )
}

function LoginForm() {
  const queryClient = useQueryClient()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const login = useMutation({
    mutationFn: () => adminApi.login(username, password),
    onSuccess: (session) => queryClient.setQueryData(adminKeys.session, session),
  })

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    login.mutate()
  }

  return (
    <form onSubmit={handleSubmit} className="mx-auto flex w-full max-w-sm flex-col gap-3">
      <h1 className="font-serif text-3xl font-bold">Administrace</h1>
      <label htmlFor="admin-username" className="font-semibold">
        Jméno
      </label>
      <input
        id="admin-username"
        autoComplete="username"
        value={username}
        onChange={(event) => setUsername(event.target.value)}
        className={input}
      />
      <label htmlFor="admin-password" className="font-semibold">
        Heslo
      </label>
      <input
        id="admin-password"
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        className={input}
      />
      {login.isError && <ErrorBanner message={login.error.message} />}
      <button type="submit" disabled={login.isPending || !username || !password} className={buttonPrimary}>
        Přihlásit
      </button>
    </form>
  )
}

function Dashboard({ username }: { username: string }) {
  const queryClient = useQueryClient()
  const logout = useMutation({
    mutationFn: adminApi.logout,
    onSettled: () => queryClient.removeQueries({ queryKey: ['admin'] }),
  })

  return (
    <>
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-serif text-3xl font-bold">Administrace</h1>
        <p className="flex items-center gap-3 text-sm text-stone-700">
          {username}
          <button
            type="button"
            onClick={() => logout.mutate()}
            className="min-h-11 font-semibold underline underline-offset-4"
          >
            Odhlásit
          </button>
        </p>
      </header>
      <NewBatchForm />
      <BatchList />
      <MonthlyReport />
    </>
  )
}

/** A 401 means the admin session expired: show the login form again. */
function useLoggedOutOn401(error: Error | null) {
  const queryClient = useQueryClient()
  useEffect(() => {
    if (error instanceof ApiError && error.status === 401) {
      queryClient.setQueryData(adminKeys.session, { username: null })
    }
  }, [error, queryClient])
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3 rounded-2xl border border-stone-300 bg-white p-5 shadow-sm">
      <h2 className="text-xl font-bold">{title}</h2>
      {children}
    </section>
  )
}

function NewBatchForm() {
  const queryClient = useQueryClient()
  const games = useQuery({ queryKey: adminKeys.games, queryFn: adminApi.games })
  const [gameId, setGameId] = useState('')
  const [quantity, setQuantity] = useState('100')
  const [validityHours, setValidityHours] = useState('5')
  const [note, setNote] = useState('')
  const [unitPrice, setUnitPrice] = useState('')
  const createBatch = useMutation({
    mutationFn: (request: CreateBatchRequest) => adminApi.createBatch(request),
    onSuccess: () => {
      setNote('')
      return queryClient.invalidateQueries({ queryKey: adminKeys.batches })
    },
  })
  useLoggedOutOn401(games.error ?? createBatch.error)

  const selectedGame = gameId || games.data?.[0]?.id || ''

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createBatch.mutate({
      gameId: selectedGame,
      quantity: Number(quantity),
      validityHours: Number(validityHours),
      note: note.trim() || null,
      unitPriceCzk: unitPrice.trim() === '' ? null : Number(unitPrice),
    })
  }

  const field = 'flex flex-col gap-1 font-semibold'
  return (
    <Section title="Nová dávka kódů">
      <p className="text-stone-700">
        Vygenerované kódy jsou nové (nepoužité). Za prodaný se kód považuje až ve chvíli, kdy ho hráč zadá.
      </p>
      <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2">
        <label className={`${field} sm:col-span-2`}>
          Hra
          <select value={selectedGame} onChange={(event) => setGameId(event.target.value)} className={input}>
            {games.data?.map((game) => (
              <option key={game.id} value={game.id}>
                {game.title} — {game.partnerName}
                {game.status !== 'PUBLISHED' ? ` (${game.status})` : ''}
              </option>
            ))}
          </select>
        </label>
        <label className={field}>
          Počet kódů
          <input
            type="number"
            min={1}
            max={5000}
            required
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            className={input}
          />
        </label>
        <label className={field}>
          Platnost od aktivace (hodiny)
          <input
            type="number"
            min={1}
            max={24}
            required
            value={validityHours}
            onChange={(event) => setValidityHours(event.target.value)}
            className={input}
          />
        </label>
        <label className={field}>
          Cena za kód (Kč, nepovinné)
          <input
            type="number"
            min={0}
            value={unitPrice}
            onChange={(event) => setUnitPrice(event.target.value)}
            className={input}
          />
        </label>
        <label className={field}>
          Poznámka (nepovinné)
          <input
            maxLength={300}
            placeholder="Sezóna 2027 — tisk 1"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            className={input}
          />
        </label>
        {createBatch.isError && (
          <div className="sm:col-span-2">
            <ErrorBanner message={createBatch.error.message} />
          </div>
        )}
        {createBatch.isSuccess && (
          <p role="status" className="font-semibold text-emerald-800 sm:col-span-2">
            Dávka {createBatch.data.quantity} kódů je vytvořená. CSV pro tisk stáhneš v seznamu dávek.
          </p>
        )}
        <button
          type="submit"
          disabled={createBatch.isPending || !selectedGame}
          className={`${buttonPrimary} sm:col-span-2`}
        >
          {createBatch.isPending ? 'Generuji…' : 'Vygenerovat kódy'}
        </button>
      </form>
    </Section>
  )
}

function BatchList() {
  const batches = useQuery({ queryKey: adminKeys.batches, queryFn: adminApi.batches })
  useLoggedOutOn401(batches.error)

  return (
    <Section title="Dávky kódů">
      {batches.isPending && <p role="status">Načítám…</p>}
      {batches.isError && <ErrorBanner message={batches.error.message} onRetry={() => void batches.refetch()} />}
      {batches.data?.length === 0 && <p>Zatím žádné dávky.</p>}
      {batches.data && batches.data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                <th className="py-2 pr-3">Vytvořeno</th>
                <th className="py-2 pr-3">Hra / partner</th>
                <th className="py-2 pr-3 text-right">Kódů</th>
                <th className="py-2 pr-3 text-right">Aktivováno</th>
                <th className="py-2 pr-3">Platnost</th>
                <th className="py-2 pr-3">Poznámka</th>
                <th className="py-2 pr-3 text-right">Cena</th>
                <th className="py-2">Tisk</th>
              </tr>
            </thead>
            <tbody>
              {batches.data.map((batch) => (
                <tr key={batch.id} className="border-b border-stone-200 align-top">
                  <td className="py-2 pr-3 whitespace-nowrap">{formatDateTime(batch.createdAt)}</td>
                  <td className="py-2 pr-3">
                    {batch.gameTitle}
                    <span className="block text-stone-600">{batch.partnerName}</span>
                  </td>
                  <td className="py-2 pr-3 text-right">{formatNumber(batch.quantity)}</td>
                  <td className="py-2 pr-3 text-right">{formatNumber(batch.activatedCount)}</td>
                  <td className="py-2 pr-3 whitespace-nowrap">
                    {batch.validityHours === null ? 'bez expirace' : `${batch.validityHours} h`}
                    {batch.multiSession && <span className="block text-stone-600">vývojový</span>}
                  </td>
                  <td className="py-2 pr-3">{batch.note ?? '—'}</td>
                  <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(batch.unitPriceCzk)}</td>
                  <td className="py-2">
                    <a
                      href={adminApi.codesCsvUrl(batch.id)}
                      download
                      className="font-semibold whitespace-nowrap underline underline-offset-4"
                    >
                      CSV
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  )
}

function MonthlyReport() {
  const [year, setYear] = useState(thisYear)
  const report = useQuery({ queryKey: adminKeys.report(year), queryFn: () => adminApi.monthlyActivations(year) })
  useLoggedOutOn401(report.error)

  const rows = report.data ?? []
  const totalCount = rows.reduce((sum, row) => sum + row.activatedCount, 0)
  const totalRevenue = rows.some((row) => row.revenueCzk !== null)
    ? rows.reduce((sum, row) => sum + (row.revenueCzk ?? 0), 0)
    : null
  const totalShare = rows.some((row) => row.partnerShareCzk !== null)
    ? rows.reduce((sum, row) => sum + (row.partnerShareCzk ?? 0), 0)
    : null

  return (
    <Section title="Aktivované kódy po měsících">
      <p className="text-stone-700">
        Podklad pro fakturaci: aktivace = prodaný kód. Měsíce podle českého času, vývojové kódy se nepočítají.
      </p>
      <label className="flex items-center gap-3 font-semibold">
        Rok
        <select
          value={year}
          onChange={(event) => setYear(Number(event.target.value))}
          className="min-h-11 rounded-xl border-2 border-stone-400 bg-white px-3 text-lg"
        >
          {[thisYear, thisYear - 1, thisYear - 2].map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </label>
      {report.isPending && <p role="status">Načítám…</p>}
      {report.isError && <ErrorBanner message={report.error.message} onRetry={() => void report.refetch()} />}
      {report.data?.length === 0 && <p>V roce {year} zatím nebyl aktivován žádný kód.</p>}
      {rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                <th className="py-2 pr-3">Měsíc</th>
                <th className="py-2 pr-3">Partner</th>
                <th className="py-2 pr-3">Hra</th>
                <th className="py-2 pr-3 text-right">Aktivováno</th>
                <th className="py-2 pr-3 text-right">Tržba</th>
                <th className="py-2 text-right">Podíl partnera</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={`${row.month}-${row.partnerName}-${row.gameTitle}`} className="border-b border-stone-200">
                  <td className="py-2 pr-3 whitespace-nowrap">{formatMonth(row.month)}</td>
                  <td className="py-2 pr-3">{row.partnerName}</td>
                  <td className="py-2 pr-3">{row.gameTitle}</td>
                  <td className="py-2 pr-3 text-right">{formatNumber(row.activatedCount)}</td>
                  <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(row.revenueCzk)}</td>
                  <td className="py-2 text-right whitespace-nowrap">{formatMoney(row.partnerShareCzk)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="font-bold">
              <tr>
                <td className="py-2 pr-3" colSpan={3}>
                  Celkem {year}
                </td>
                <td className="py-2 pr-3 text-right">{formatNumber(totalCount)}</td>
                <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(totalRevenue)}</td>
                <td className="py-2 text-right whitespace-nowrap">{formatMoney(totalShare)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
      <button
        type="button"
        onClick={() => void report.refetch()}
        className="min-h-11 self-start font-semibold underline underline-offset-4"
      >
        Obnovit
      </button>
    </Section>
  )
}
