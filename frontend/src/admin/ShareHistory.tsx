import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type Partner, type SetShareRequest } from './adminApi.ts'
import { firstOfNextMonth, formatDate, formatPercent, todayInPrague } from './format.ts'
import Section from './Section.tsx'
import { invalidateAdminData, linkButton, useLoggedOutOn401 } from './shared.ts'

const cell = 'min-h-11 w-full rounded-lg border-2 border-stone-400 bg-white px-2'

/** Castle's share of the revenue over time: valid from a day until the next change. */
export default function ShareHistory({ partner }: { partner: Partner }) {
  const queryClient = useQueryClient()
  const today = todayInPrague()
  const [validFrom, setValidFrom] = useState(() => firstOfNextMonth(today))
  const [sharePct, setSharePct] = useState('')
  // The share changes the money in the report and the key figures, too
  const refresh = () => invalidateAdminData(queryClient)
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
    <Section title="Podíl hradu z tržby">
      <p className="text-stone-700">
        Podíl platí od zadaného dne do další změny. Každý aktivovaný kód se vyúčtuje podle podílu platného v den
        aktivace (český čas). Změnu obvykle zadáte od prvního dne měsíce.
      </p>
      {partner.shares.length === 0 ? (
        <p className="font-semibold text-amber-900">
          Zatím není nastavený žádný podíl — tržba se vyúčtuje bez podílu hradu.
        </p>
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
    </Section>
  )
}
