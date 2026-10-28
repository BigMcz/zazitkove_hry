import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, partnerApi, type BatchSummary } from './adminApi.ts'
import { formatDateTime, formatMoney, formatNumber } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, invalidateAdminData, linkButton, useLoggedOutOn401, type Scope } from './shared.ts'

interface BatchListProps {
  scope: Scope
  /** Operator: batches of this partner. */
  partnerId?: string
}

/**
 * Operator: batches of a partner with their state (in production / handed over), CSV for printing and
 * the handover. Castle: deliveries of cards it received, with how many are still unused.
 */
export default function BatchList({ scope, partnerId }: BatchListProps) {
  const isAdmin = scope === 'admin'
  const batches = useQuery({
    queryKey: adminKeys.batches(scope, partnerId),
    queryFn: () => (isAdmin ? adminApi.batches(partnerId) : partnerApi.batches()),
  })
  useLoggedOutOn401(batches.error)

  return (
    <Section title={isAdmin ? 'Dávky kódů' : 'Převzaté kartičky'}>
      {isAdmin ? (
        <p className="text-stone-700">
          Postup: vygenerovat → stáhnout CSV a nechat vyrobit → po předání hradu <strong>označit jako předané</strong>.
          Do té doby kódy nefungují (nikdo je nemůže použít, ani ve výrobě) a hrad dávku nevidí.
        </p>
      ) : (
        <p className="text-stone-700">
          „Zbývá“ je počet kartiček z dodávky, které zatím nikdo neaktivoval — při inventuře byste jich neměli mít
          víc.
        </p>
      )}
      {batches.isPending && <p role="status">Načítám…</p>}
      {batches.isError && <ErrorBanner message={batches.error.message} onRetry={() => void batches.refetch()} />}
      {batches.data?.length === 0 && <p>{isAdmin ? 'Zatím žádné dávky.' : 'Zatím jste nepřevzali žádné kartičky.'}</p>}
      {batches.data && batches.data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                <th className="py-2 pr-3">{isAdmin ? 'Vytvořeno' : 'Převzato'}</th>
                <th className="py-2 pr-3">Hra</th>
                {isAdmin && <th className="py-2 pr-3">Stav</th>}
                <th className="py-2 pr-3 text-right">Kusů</th>
                <th className="py-2 pr-3 text-right">Aktivováno</th>
                <th className="py-2 pr-3 text-right">Zbývá</th>
                {isAdmin && <th className="py-2 pr-3">Platnost</th>}
                <th className="py-2 pr-3">Poznámka</th>
                {isAdmin && <th className="py-2 pr-3 text-right">Cena</th>}
                {isAdmin && <th className="py-2">Akce</th>}
              </tr>
            </thead>
            <tbody>
              {batches.data.map((batch) => (
                <BatchRow key={batch.id} batch={batch} isAdmin={isAdmin} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  )
}

function BatchRow({ batch, isAdmin }: { batch: BatchSummary; isAdmin: boolean }) {
  const queryClient = useQueryClient()
  const handOver = useMutation({
    mutationFn: () => (batch.handedOverAt ? adminApi.undoHandOver(batch.id) : adminApi.handOver(batch.id)),
    // Stock and production counts change too
    onSuccess: () => invalidateAdminData(queryClient),
  })
  useLoggedOutOn401(handOver.error)

  const inProduction = batch.handedOverAt === null
  return (
    <tr className="border-b border-stone-200 align-top">
      <td className="py-2 pr-3 whitespace-nowrap">
        {formatDateTime(isAdmin ? batch.createdAt : (batch.handedOverAt ?? batch.createdAt))}
      </td>
      <td className="py-2 pr-3">{batch.gameTitle}</td>
      {isAdmin && (
        <td className="py-2 pr-3 whitespace-nowrap">
          {inProduction ? (
            <span className="rounded-full bg-amber-100 px-2 py-0.5 font-semibold text-amber-900">Ve výrobě</span>
          ) : (
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 font-semibold text-emerald-900">
              Předáno {formatDateTime(batch.handedOverAt as string)}
            </span>
          )}
          {batch.multiSession && <span className="block text-stone-600">vývojový</span>}
        </td>
      )}
      <td className="py-2 pr-3 text-right">{formatNumber(batch.quantity)}</td>
      <td className="py-2 pr-3 text-right">{formatNumber(batch.activatedCount)}</td>
      <td className="py-2 pr-3 text-right font-semibold">{formatNumber(batch.notActivatedCount)}</td>
      {isAdmin && (
        <td className="py-2 pr-3 whitespace-nowrap">
          {batch.validityHours === null ? 'bez expirace' : `${batch.validityHours} h`}
        </td>
      )}
      <td className="py-2 pr-3">{batch.note ?? '—'}</td>
      {isAdmin && <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(batch.unitPriceCzk)}</td>}
      {isAdmin && (
        <td className="py-2">
          <div className="flex flex-col items-start gap-1">
            <a href={adminApi.codesCsvUrl(batch.id)} download className={`${linkButton} inline-flex items-center`}>
              CSV pro tisk
            </a>
            {(inProduction || batch.activatedCount === 0) && (
              <button
                type="button"
                disabled={handOver.isPending}
                onClick={() => handOver.mutate()}
                className={linkButton}
              >
                {inProduction ? 'Označit jako předané' : 'Vrátit předání'}
              </button>
            )}
            {handOver.isError && (
              <span role="alert" className="text-red-800">
                {handOver.error.message}
              </span>
            )}
          </div>
        </td>
      )}
    </tr>
  )
}
