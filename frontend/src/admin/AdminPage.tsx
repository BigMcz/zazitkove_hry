import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router'
import logoUrl from '../assets/storyhold.svg'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { adminApi, type AdminSession } from './adminApi.ts'
import CastleOverview from './CastleOverview.tsx'
import Dashboard from './Dashboard.tsx'
import PartnerDetail from './PartnerDetail.tsx'
import PartnerNav from './PartnerNav.tsx'
import PasswordButton from './PasswordButton.tsx'
import { adminKeys, endSession } from './shared.ts'

/**
 * One login for two roles. The operator gets a dashboard with a left menu of castles (partners) and a
 * detail of each castle; castle staff get the overview of their own castle only.
 */
export default function AdminPage() {
  const session = useQuery({ queryKey: adminKeys.session, queryFn: adminApi.session })

  return (
    <div className="min-h-dvh font-display">
      <TopBar session={session.data} />
      <main className="flex w-full flex-col gap-6 px-4 pt-6 pb-12 lg:px-8">
        {session.isPending && <p role="status">Načítám…</p>}
        {session.isError && <ErrorBanner message={session.error.message} onRetry={() => void session.refetch()} />}
        {session.data && <Content session={session.data} />}
      </main>
    </div>
  )
}

/** Dark bar with the logo as on the presentation pages; the logged-in user, password and logout on the right. */
function TopBar({ session }: { session?: AdminSession }) {
  const queryClient = useQueryClient()
  const logout = useMutation({
    mutationFn: adminApi.logout,
    onSettled: () => endSession(queryClient),
  })

  return (
    <header className="bg-night text-white">
      <div className="flex w-full flex-wrap items-center justify-between gap-x-6 gap-y-2 px-4 py-3 lg:px-8">
        <div className="flex items-center gap-4">
          <Link
            to="/admin"
            className="rounded focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-lime"
          >
            <img src={logoUrl} alt="StoryHold, administrace" className="h-7 w-auto" />
          </Link>
          <span className="text-xs font-bold tracking-[0.18em] text-lime uppercase">
            {session?.role === 'PARTNER' ? 'Správa hradu' : 'Administrace'}
          </span>
        </div>
        {session?.username && (
          <div className="flex items-center gap-3 text-sm text-stone-300">
            {session.username}
            {session.role === 'PARTNER' && <PasswordButton />}
            <button
              type="button"
              onClick={() => logout.mutate()}
              className="min-h-11 font-semibold text-white underline underline-offset-4 hover:text-lime"
            >
              Odhlásit
            </button>
          </div>
        )}
      </div>
    </header>
  )
}

function Content({ session }: { session: AdminSession }) {
  if (!session.username) {
    return <LoginForm />
  }
  if (session.role === 'PARTNER') {
    return (
      <>
        <h1 className="text-3xl font-bold">{session.partnerName ?? 'Přehled hradu'}</h1>
        <CastleOverview />
      </>
    )
  }
  return (
    <>
      <div className="grid gap-6 md:grid-cols-[15rem_1fr]">
        <aside className="md:border-r md:border-stone-300 md:pr-4">
          <PartnerNav />
        </aside>
        <div className="flex min-w-0 flex-col gap-6">
          <Routes>
            <Route index element={<Dashboard />} />
            <Route path="partneri/:partnerId" element={<PartnerDetail />} />
            <Route path="*" element={<Navigate to="/admin" replace />} />
          </Routes>
        </div>
      </div>
    </>
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
    <form onSubmit={handleSubmit} className="mx-auto mt-6 flex w-full max-w-sm flex-col gap-3">
      <h1 className="text-3xl font-bold">Administrace</h1>
      <p className="text-stone-700">Pro správce aplikace a správu hradů.</p>
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
