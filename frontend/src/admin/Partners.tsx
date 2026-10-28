import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type Partner, type UpdatePartnerRequest } from './adminApi.ts'
import { formatPercent } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, linkButton, useLoggedOutOn401 } from './shared.ts'

/** Castles (partners): name, contact and their share of the revenue. */
export default function Partners() {
  const partners = useQuery({ queryKey: adminKeys.partners, queryFn: adminApi.partners })
  useLoggedOutOn401(partners.error)

  return (
    <Section title="Hrady a podíl">
      <p className="text-stone-700">
        Podíl hradu z tržby se při generování zapíše do dávky kódů. Změna podílu proto platí pro nově vygenerované
        dávky — už vytištěné kódy a vyfakturované měsíce se nepřepočítají.
      </p>
      {partners.isPending && <p role="status">Načítám…</p>}
      {partners.isError && <ErrorBanner message={partners.error.message} onRetry={() => void partners.refetch()} />}
      {partners.data && partners.data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                <th className="py-2 pr-3">Hrad (partner)</th>
                <th className="py-2 pr-3">Kontakt</th>
                <th className="py-2 pr-3 text-right">Podíl hradu</th>
                <th className="py-2">Akce</th>
              </tr>
            </thead>
            <tbody>
              {partners.data.map((partner) => (
                <PartnerRow key={partner.id} partner={partner} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  )
}

function PartnerRow({ partner }: { partner: Partner }) {
  const [editing, setEditing] = useState(false)

  if (editing) {
    return <PartnerEditRow partner={partner} onDone={() => setEditing(false)} />
  }
  return (
    <tr className="border-b border-stone-200 align-top">
      <td className="py-2 pr-3 font-semibold">{partner.name}</td>
      <td className="py-2 pr-3">{partner.contactEmail ?? '—'}</td>
      <td className="py-2 pr-3 text-right">{formatPercent(partner.revenueSharePct)}</td>
      <td className="py-2">
        <button type="button" onClick={() => setEditing(true)} className={linkButton}>
          Upravit
        </button>
      </td>
    </tr>
  )
}

function PartnerEditRow({ partner, onDone }: { partner: Partner; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(partner.name)
  const [email, setEmail] = useState(partner.contactEmail ?? '')
  const [share, setShare] = useState(partner.revenueSharePct === null ? '' : String(partner.revenueSharePct))
  const update = useMutation({
    mutationFn: (request: UpdatePartnerRequest) => adminApi.updatePartner(partner.id, request),
    onSuccess: async () => {
      // Names appear in games, batches, accounts and the report
      await queryClient.invalidateQueries({ queryKey: ['admin'], predicate: (query) => query.queryKey[1] !== 'session' })
      onDone()
    },
  })
  useLoggedOutOn401(update.error)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    update.mutate({ name: name.trim(), contactEmail: email.trim() || null, revenueSharePct: Number(share) })
  }

  const cell = 'min-h-11 w-full rounded-lg border-2 border-stone-400 bg-white px-2'
  const formId = `partner-${partner.id}`
  return (
    <tr className="border-b border-stone-200 align-top">
      <td className="py-2 pr-3">
        <input
          form={formId}
          aria-label="Název hradu"
          required
          maxLength={200}
          value={name}
          onChange={(event) => setName(event.target.value)}
          className={cell}
        />
      </td>
      <td className="py-2 pr-3">
        <input
          form={formId}
          type="email"
          aria-label="Kontaktní e-mail"
          maxLength={200}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          className={cell}
        />
      </td>
      <td className="py-2 pr-3">
        <span className="flex items-center justify-end gap-1">
          <input
            form={formId}
            type="number"
            aria-label="Podíl hradu v procentech"
            required
            min={0}
            max={100}
            step={0.01}
            value={share}
            onChange={(event) => setShare(event.target.value)}
            className={`${cell} max-w-24 text-right`}
          />
          %
        </span>
      </td>
      <td className="py-2">
        <form id={formId} onSubmit={handleSubmit} className="flex flex-col items-start gap-1">
          <button type="submit" disabled={update.isPending} className={linkButton}>
            Uložit
          </button>
          <button type="button" onClick={onDone} className={linkButton}>
            Zrušit
          </button>
          {update.isError && (
            <span role="alert" className="text-red-800">
              {update.error.message}
            </span>
          )}
        </form>
      </td>
    </tr>
  )
}
