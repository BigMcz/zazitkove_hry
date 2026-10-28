import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type Partner, type SetShareRequest, type UpdatePartnerRequest } from './adminApi.ts'
import { firstOfNextMonth, formatDate, formatPercent, todayInPrague } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, linkButton, useLoggedOutOn401 } from './shared.ts'

const cell = 'min-h-11 w-full rounded-lg border-2 border-stone-400 bg-white px-2'

/** Castles (partners): name, contact and their share of the revenue over time. */
export default function Partners() {
  const partners = useQuery({ queryKey: adminKeys.partners, queryFn: adminApi.partners })
  useLoggedOutOn401(partners.error)

  return (
    <Section title="Hrady a podíl">
      <p className="text-stone-700">
        Podíl hradu z tržby platí od zadaného dne do další změny. Každý aktivovaný kód se vyúčtuje podle podílu
        platného v den aktivace (český čas). Změnu obvykle zadáte od prvního dne měsíce.
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
                <th className="py-2 pr-3 text-right">Podíl dnes</th>
                <th className="py-2 pr-3">Naplánovaná změna</th>
                <th className="py-2">Akce</th>
              </tr>
            </thead>
            <tbody>
              {partners.data.map((partner) => (
                <PartnerRows key={partner.id} partner={partner} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  )
}

type Mode = 'view' | 'edit' | 'shares'

function PartnerRows({ partner }: { partner: Partner }) {
  const [mode, setMode] = useState<Mode>('view')
  const today = todayInPrague()
  const planned = partner.shares.filter((share) => share.validFrom > today)

  return (
    <>
      {mode === 'edit' ? (
        <PartnerEditRow partner={partner} onDone={() => setMode('view')} />
      ) : (
        <tr className="border-b border-stone-200 align-top">
          <td className="py-2 pr-3 font-semibold">{partner.name}</td>
          <td className="py-2 pr-3">{partner.contactEmail ?? '—'}</td>
          <td className="py-2 pr-3 text-right">{formatPercent(partner.currentSharePct)}</td>
          <td className="py-2 pr-3">
            {planned.length === 0
              ? '—'
              : planned.map((share) => (
                  <span key={share.id} className="block whitespace-nowrap">
                    od {formatDate(share.validFrom)}: {formatPercent(share.sharePct)}
                  </span>
                ))}
          </td>
          <td className="py-2">
            <div className="flex flex-col items-start gap-1">
              <button type="button" onClick={() => setMode('edit')} className={linkButton}>
                Upravit
              </button>
              <button
                type="button"
                aria-expanded={mode === 'shares'}
                onClick={() => setMode(mode === 'shares' ? 'view' : 'shares')}
                className={linkButton}
              >
                {mode === 'shares' ? 'Skrýt podíly' : 'Podíly'}
              </button>
            </div>
          </td>
        </tr>
      )}
      {mode === 'shares' && (
        <tr className="border-b border-stone-200 bg-stone-50">
          <td colSpan={5} className="p-3">
            <ShareHistory partner={partner} />
          </td>
        </tr>
      )}
    </>
  )
}

function ShareHistory({ partner }: { partner: Partner }) {
  const queryClient = useQueryClient()
  const today = todayInPrague()
  const [validFrom, setValidFrom] = useState(() => firstOfNextMonth(today))
  const [sharePct, setSharePct] = useState('')
  const refresh = () => queryClient.invalidateQueries({ queryKey: adminKeys.partners })
  const setShare = useMutation({
    mutationFn: (request: SetShareRequest) => adminApi.setPartnerShare(partner.id, request),
    onSuccess: () => {
      setSharePct('')
      return refresh()
    },
  })
  const deleteShare = useMutation({
    mutationFn: (shareId: string) => adminApi.deletePartnerShare(partner.id, shareId),
    onSuccess: refresh,
  })
  useLoggedOutOn401(setShare.error ?? deleteShare.error)

  const currentId = partner.shares.filter((share) => share.validFrom <= today).at(-1)?.id
  const replacesExisting = partner.shares.some((share) => share.validFrom === validFrom)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setShare.mutate({ validFrom, sharePct: Number(sharePct) })
  }

  return (
    <div className="flex flex-col gap-3">
      <h3 className="font-bold">Podíly hradu {partner.name}</h3>
      {partner.shares.length === 0 ? (
        <p>Zatím není nastavený žádný podíl — tržba se vyúčtuje bez podílu hradu.</p>
      ) : (
        <table className="w-auto text-left text-sm">
          <thead className="text-stone-700">
            <tr>
              <th className="py-1 pr-6">Platí od</th>
              <th className="py-1 pr-6 text-right">Podíl</th>
              <th className="py-1" />
            </tr>
          </thead>
          <tbody>
            {partner.shares.map((share) => (
              <tr key={share.id}>
                <td className="py-1 pr-6 whitespace-nowrap">
                  {formatDate(share.validFrom)}
                  {share.id === currentId && <span className="ml-2 font-semibold text-emerald-800">platí dnes</span>}
                  {share.validFrom > today && <span className="ml-2 text-stone-600">naplánováno</span>}
                </td>
                <td className="py-1 pr-6 text-right">{formatPercent(share.sharePct)}</td>
                <td className="py-1">
                  <button
                    type="button"
                    disabled={deleteShare.isPending}
                    onClick={() => deleteShare.mutate(share.id)}
                    aria-label={`Smazat podíl od ${formatDate(share.validFrom)}`}
                    className={linkButton}
                  >
                    Smazat
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 font-semibold">
          Platí od
          <input
            type="date"
            required
            value={validFrom}
            onChange={(event) => setValidFrom(event.target.value)}
            className={cell}
          />
        </label>
        <label className="flex flex-col gap-1 font-semibold">
          Podíl (%)
          <input
            type="number"
            required
            min={0}
            max={100}
            step={0.01}
            value={sharePct}
            onChange={(event) => setSharePct(event.target.value)}
            className={`${cell} max-w-28`}
          />
        </label>
        <button type="submit" disabled={setShare.isPending} className={`${linkButton} pb-2`}>
          {replacesExisting ? 'Nahradit podíl' : 'Přidat podíl'}
        </button>
      </form>
      {validFrom && validFrom <= today && (
        <p role="note" className="text-amber-900">
          Datum není v budoucnosti — změní vyúčtování kódů aktivovaných od {formatDate(validFrom)}, tedy i měsíců, které
          už mohly být vyfakturované.
        </p>
      )}
      {(setShare.error ?? deleteShare.error) && (
        <ErrorBanner message={(setShare.error ?? deleteShare.error)?.message ?? ''} />
      )}
    </div>
  )
}

function PartnerEditRow({ partner, onDone }: { partner: Partner; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState(partner.name)
  const [email, setEmail] = useState(partner.contactEmail ?? '')
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
    update.mutate({ name: name.trim(), contactEmail: email.trim() || null })
  }

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
      <td className="py-2 pr-3 text-right">{formatPercent(partner.currentSharePct)}</td>
      <td className="py-2 pr-3 text-stone-600">podíly přes „Podíly“</td>
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
