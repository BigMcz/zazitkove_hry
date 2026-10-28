import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { adminApi, MIN_PASSWORD_LENGTH, type CreatePartnerUserRequest, type PartnerUser } from './adminApi.ts'
import { formatDateTime } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, fieldLabel, linkButton, useLoggedOutOn401 } from './shared.ts'

/** Accounts of castle staff: they log in at the same address and see only their castle. */
export default function PartnerUsers() {
  const users = useQuery({ queryKey: adminKeys.partnerUsers, queryFn: adminApi.partnerUsers })
  useLoggedOutOn401(users.error)

  return (
    <Section title="Účty hradů (partnerů)">
      <p className="text-stone-700">
        Správa hradu se přihlašuje na této stránce a vidí jen své vytištěné kódy a měsíční počty aktivací. Počáteční
        heslo jim předejte bezpečně, mohou si ho pak změnit.
      </p>
      <NewPartnerUserForm />
      {users.isPending && <p role="status">Načítám…</p>}
      {users.isError && <ErrorBanner message={users.error.message} onRetry={() => void users.refetch()} />}
      {users.data?.length === 0 && <p>Zatím žádné účty.</p>}
      {users.data && users.data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                <th className="py-2 pr-3">Jméno</th>
                <th className="py-2 pr-3">Partner</th>
                <th className="py-2 pr-3">Stav</th>
                <th className="py-2 pr-3">Poslední přihlášení</th>
                <th className="py-2">Akce</th>
              </tr>
            </thead>
            <tbody>
              {users.data.map((user) => (
                <PartnerUserRow key={user.id} user={user} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  )
}

function NewPartnerUserForm() {
  const queryClient = useQueryClient()
  const partners = useQuery({ queryKey: adminKeys.partners, queryFn: adminApi.partners })
  const [partnerId, setPartnerId] = useState('')
  const [username, setUsername] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const create = useMutation({
    mutationFn: (request: CreatePartnerUserRequest) => adminApi.createPartnerUser(request),
    onSuccess: () => {
      setUsername('')
      setDisplayName('')
      setPassword('')
      return queryClient.invalidateQueries({ queryKey: adminKeys.partnerUsers })
    },
  })
  useLoggedOutOn401(partners.error ?? create.error)

  const selectedPartner = partnerId || partners.data?.[0]?.id || ''

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    create.mutate({ partnerId: selectedPartner, username: username.trim(), displayName: displayName.trim() || null, password })
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-4 border-b border-stone-200 pb-5 sm:grid-cols-2">
      <label className={fieldLabel}>
        Partner
        <select value={selectedPartner} onChange={(event) => setPartnerId(event.target.value)} className={input}>
          {partners.data?.map((partner) => (
            <option key={partner.id} value={partner.id}>
              {partner.name}
            </option>
          ))}
        </select>
      </label>
      <label className={fieldLabel}>
        Přihlašovací jméno
        <input
          required
          minLength={3}
          maxLength={100}
          pattern="[A-Za-z0-9._@\-]+"
          autoComplete="off"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          className={input}
        />
      </label>
      <label className={fieldLabel}>
        Popis (nepovinné)
        <input
          maxLength={200}
          placeholder="Pokladna hradu Loket"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          className={input}
        />
      </label>
      <label className={fieldLabel}>
        Počáteční heslo (min. {MIN_PASSWORD_LENGTH} znaků)
        <input
          type="text"
          required
          minLength={MIN_PASSWORD_LENGTH}
          maxLength={100}
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          className={input}
        />
      </label>
      {create.isError && (
        <div className="sm:col-span-2">
          <ErrorBanner message={create.error.message} />
        </div>
      )}
      {create.isSuccess && (
        <p role="status" className="font-semibold text-emerald-800 sm:col-span-2">
          Účet {create.data.username} pro {create.data.partnerName} je vytvořený.
        </p>
      )}
      <button
        type="submit"
        disabled={create.isPending || !selectedPartner}
        className={`${buttonPrimary} sm:col-span-2`}
      >
        Vytvořit účet
      </button>
    </form>
  )
}

function PartnerUserRow({ user }: { user: PartnerUser }) {
  const queryClient = useQueryClient()
  const [settingPassword, setSettingPassword] = useState(false)
  const [password, setPassword] = useState('')
  const refresh = () => queryClient.invalidateQueries({ queryKey: adminKeys.partnerUsers })
  const setEnabled = useMutation({
    mutationFn: (enabled: boolean) => adminApi.setPartnerUserEnabled(user.id, enabled),
    onSuccess: refresh,
  })
  const setNewPassword = useMutation({
    mutationFn: () => adminApi.setPartnerUserPassword(user.id, password),
    onSuccess: () => {
      setPassword('')
      setSettingPassword(false)
    },
  })
  useLoggedOutOn401(setEnabled.error ?? setNewPassword.error)

  function handlePassword(event: FormEvent) {
    event.preventDefault()
    setNewPassword.mutate()
  }

  return (
    <tr className="border-b border-stone-200 align-top">
      <td className="py-2 pr-3">
        <span className="font-semibold">{user.username}</span>
        {user.displayName && <span className="block text-stone-600">{user.displayName}</span>}
      </td>
      <td className="py-2 pr-3">{user.partnerName}</td>
      <td className="py-2 pr-3">{user.enabled ? 'aktivní' : <strong className="text-red-800">zablokovaný</strong>}</td>
      <td className="py-2 pr-3 whitespace-nowrap">{user.lastLoginAt ? formatDateTime(user.lastLoginAt) : '—'}</td>
      <td className="py-2">
        <div className="flex flex-col items-start gap-1">
          <button
            type="button"
            disabled={setEnabled.isPending}
            onClick={() => setEnabled.mutate(!user.enabled)}
            className={linkButton}
          >
            {user.enabled ? 'Zablokovat' : 'Odblokovat'}
          </button>
          {!settingPassword && (
            <button type="button" onClick={() => setSettingPassword(true)} className={linkButton}>
              Nastavit heslo
            </button>
          )}
          {settingPassword && (
            <form onSubmit={handlePassword} className="flex flex-wrap items-center gap-2">
              <input
                type="text"
                aria-label={`Nové heslo pro ${user.username}`}
                required
                minLength={MIN_PASSWORD_LENGTH}
                maxLength={100}
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="min-h-11 rounded-lg border-2 border-stone-400 px-2"
              />
              <button type="submit" disabled={setNewPassword.isPending} className={linkButton}>
                Uložit
              </button>
              <button type="button" onClick={() => setSettingPassword(false)} className={linkButton}>
                Zrušit
              </button>
            </form>
          )}
          {setNewPassword.isSuccess && <span role="status">Heslo je změněné.</span>}
          {(setEnabled.error ?? setNewPassword.error) && (
            <span role="alert" className="text-red-800">
              {(setEnabled.error ?? setNewPassword.error)?.message}
            </span>
          )}
        </div>
      </td>
    </tr>
  )
}
