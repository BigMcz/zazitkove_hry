import { useQuery } from '@tanstack/react-query'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, partnerApi } from './adminApi.ts'
import { formatDateTime, formatMoney, formatNumber } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, useLoggedOutOn401, type Scope } from './shared.ts'

/** Batches of codes; the operator also sees the partner and downloads the CSV for printing. */
export default function BatchList({ scope }: { scope: Scope }) {
  const isAdmin = scope === 'admin'
  const batches = useQuery({
    queryKey: adminKeys.batches(scope),
    queryFn: isAdmin ? adminApi.batches : partnerApi.batches,
  })
  useLoggedOutOn401(batches.error)

  return (
    <Section title={isAdmin ? 'Dávky kódů' : 'Vytištěné kódy'}>
      {batches.isPending && <p role="status">Načítám…</p>}
      {batches.isError && <ErrorBanner message={batches.error.message} onRetry={() => void batches.refetch()} />}
      {batches.data?.length === 0 && <p>Zatím žádné dávky.</p>}
      {batches.data && batches.data.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                <th className="py-2 pr-3">Vytvořeno</th>
                <th className="py-2 pr-3">{isAdmin ? 'Hra / partner' : 'Hra'}</th>
                <th className="py-2 pr-3 text-right">Kódů</th>
                <th className="py-2 pr-3 text-right">Aktivováno</th>
                <th className="py-2 pr-3">Platnost</th>
                <th className="py-2 pr-3">Poznámka</th>
                <th className="py-2 pr-3 text-right">Cena</th>
                {isAdmin && <th className="py-2">Tisk</th>}
              </tr>
            </thead>
            <tbody>
              {batches.data.map((batch) => (
                <tr key={batch.id} className="border-b border-stone-200 align-top">
                  <td className="py-2 pr-3 whitespace-nowrap">{formatDateTime(batch.createdAt)}</td>
                  <td className="py-2 pr-3">
                    {batch.gameTitle}
                    {isAdmin && <span className="block text-stone-600">{batch.partnerName}</span>}
                  </td>
                  <td className="py-2 pr-3 text-right">{formatNumber(batch.quantity)}</td>
                  <td className="py-2 pr-3 text-right">{formatNumber(batch.activatedCount)}</td>
                  <td className="py-2 pr-3 whitespace-nowrap">
                    {batch.validityHours === null ? 'bez expirace' : `${batch.validityHours} h`}
                    {batch.multiSession && <span className="block text-stone-600">vývojový</span>}
                  </td>
                  <td className="py-2 pr-3">{batch.note ?? '—'}</td>
                  <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(batch.unitPriceCzk)}</td>
                  {isAdmin && (
                    <td className="py-2">
                      <a
                        href={adminApi.codesCsvUrl(batch.id)}
                        download
                        className="font-semibold whitespace-nowrap underline underline-offset-4"
                      >
                        CSV
                      </a>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Section>
  )
}
