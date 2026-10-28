import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { adminApi, type AdminSession } from './adminApi.ts'
import BatchList from './BatchList.tsx'
import MonthlyReport from './MonthlyReport.tsx'
import NewBatchForm from './NewBatchForm.tsx'
import PartnerUsers from './PartnerUsers.tsx'
import Partners from './Partners.tsx'
import PasswordButton from './PasswordButton.tsx'
import Tabs, { type Tab } from './Tabs.tsx'
import { adminKeys, endSession, linkButton } from './shared.ts'

/**
 * One login for two roles: the operator (batches of codes, CSV, partner accounts, report of all
 * partners) and castle staff (their printed codes and monthly activations for invoicing).
 */
export default function AdminPage() {
  const session = useQuery({ queryKey: adminKeys.session, queryFn: adminApi.session })

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-8 px-4 pt-6 pb-12">
      {session.isPending && <p role="status">Načítám…</p>}
      {session.isError && <ErrorBanner message={session.error.message} onRetry={() => void session.refetch()} />}
      {session.data && <Content session={session.data} />}
    </main>
  )
}

type AdminTab = 'kody' | 'aktivace' | 'hrady'

const adminTabs: Tab<AdminTab>[] = [
  { id: 'kody', label: 'Kódy a tisk' },
  { id: 'aktivace', label: 'Aktivace a fakturace' },
  { id: 'hrady', label: 'Hrady a účty' },
]

function Content({ session }: { session: AdminSession }) {
  if (!session.username) {
    return <LoginForm />
  }
  if (session.role === 'PARTNER') {
    return (
      <>
        <Header title={session.partnerName ?? 'Přehled hradu'} username={session.username}>
          <PasswordButton />
        </Header>
        <MonthlyReport scope="partner" />
        <BatchList scope="partner" />
      </>
    )
  }
  return (
    <>
      <Header title="Administrace" username={session.username} />
      <AdminTabs />
    </>
  )
}

/** The active tab is kept in the URL (?sekce=…), so it survives a reload. */
function AdminTabs() {
  const [searchParams, setSearchParams] = useSearchParams()
  const requested = searchParams.get('sekce')
  const active = adminTabs.find((tab) => tab.id === requested)?.id ?? 'kody'

  return (
    <Tabs tabs={adminTabs} active={active} onChange={(id) => setSearchParams({ sekce: id }, { replace: true })}>
      {active === 'kody' && (
        <>
          <NewBatchForm />
          <BatchList scope="admin" />
        </>
      )}
      {active === 'aktivace' && <MonthlyReport scope="admin" />}
      {active === 'hrady' && (
        <>
          <Partners />
          <PartnerUsers />
        </>
      )}
    </Tabs>
  )
}

function Header({ title, username, children }: { title: string; username: string; children?: ReactNode }) {
  const queryClient = useQueryClient()
  const logout = useMutation({
    mutationFn: adminApi.logout,
    onSettled: () => endSession(queryClient),
  })

  return (
    <header className="flex flex-wrap items-center justify-between gap-3">
      <h1 className="font-serif text-3xl font-bold">{title}</h1>
      <div className="flex items-center gap-3 text-sm text-stone-700">
        {username}
        {children}
        <button type="button" onClick={() => logout.mutate()} className={linkButton}>
          Odhlásit
        </button>
      </div>
    </header>
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
