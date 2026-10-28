import { useQuery } from '@tanstack/react-query'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, partnerApi } from './adminApi.ts'
import { formatMoney, formatMonth, formatNumber } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, useLoggedOutOn401, type Scope } from './shared.ts'

interface MonthlyReportProps {
  scope: Scope
  year: number
  /** Operator: only this partner (the partner column is then hidden); all partners when missing. */
  partnerId?: string
}

/** Activated (= sold) codes per month — the basis for invoicing between the operator and the castle. */
export default function MonthlyReport({ scope, year, partnerId }: MonthlyReportProps) {
  const isAdmin = scope === 'admin'
  const showPartner = isAdmin && !partnerId
  const report = useQuery({
    queryKey: adminKeys.report(scope, year, partnerId),
    queryFn: () => (isAdmin ? adminApi.monthlyActivations(year, partnerId) : partnerApi.monthlyActivations(year)),
  })
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
    <Section title={`Aktivace po měsících ${year}`}>
      <p className="text-stone-700">
        {isAdmin
          ? 'Podklad pro fakturaci: aktivace = prodaný kód, podíl hradu podle podílu platného v den aktivace. Měsíce podle českého času, vývojové kódy se nepočítají.'
          : 'Kód se počítá jako prodaný ve chvíli, kdy ho hráč poprvé zadá. Měsíce podle českého času.'}
      </p>
      {report.isPending && <p role="status">Načítám…</p>}
      {report.isError && <ErrorBanner message={report.error.message} onRetry={() => void report.refetch()} />}
      {report.data?.length === 0 && <p>V roce {year} zatím nebyl aktivován žádný kód.</p>}
      {rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                <th className="py-2 pr-3">Měsíc</th>
                {showPartner && <th className="py-2 pr-3">Partner</th>}
                <th className="py-2 pr-3">Hra</th>
                <th className="py-2 pr-3 text-right">Aktivováno</th>
                <th className="py-2 pr-3 text-right">Tržba</th>
                <th className="py-2 text-right">{isAdmin ? 'Podíl hradu' : 'Váš podíl'}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={`${row.month}-${row.partnerName}-${row.gameTitle}`} className="border-b border-stone-200">
                  <td className="py-2 pr-3 whitespace-nowrap">{formatMonth(row.month)}</td>
                  {showPartner && <td className="py-2 pr-3">{row.partnerName}</td>}
                  <td className="py-2 pr-3">{row.gameTitle}</td>
                  <td className="py-2 pr-3 text-right">{formatNumber(row.activatedCount)}</td>
                  <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(row.revenueCzk)}</td>
                  <td className="py-2 text-right whitespace-nowrap">{formatMoney(row.partnerShareCzk)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="font-bold">
              <tr>
                <td className="py-2 pr-3" colSpan={showPartner ? 3 : 2}>
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
    </Section>
  )
}
