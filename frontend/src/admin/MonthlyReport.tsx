import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, partnerApi } from './adminApi.ts'
import { formatMoney, formatMonth, formatNumber } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, linkButton, thisYear, useLoggedOutOn401, type Scope } from './shared.ts'

/** Activated (= sold) codes per month — the basis for invoicing between the operator and the castle. */
export default function MonthlyReport({ scope }: { scope: Scope }) {
  const isAdmin = scope === 'admin'
  const [year, setYear] = useState(thisYear)
  const report = useQuery({
    queryKey: adminKeys.report(scope, year),
    queryFn: () => (isAdmin ? adminApi.monthlyActivations(year) : partnerApi.monthlyActivations(year)),
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
    <Section title="Aktivované kódy po měsících">
      <p className="text-stone-700">
        {isAdmin
          ? 'Podklad pro fakturaci: aktivace = prodaný kód. Měsíce podle českého času, vývojové kódy se nepočítají.'
          : 'Kód se počítá jako prodaný ve chvíli, kdy ho hráč poprvé zadá. Měsíce podle českého času.'}
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
                {isAdmin && <th className="py-2 pr-3">Partner</th>}
                <th className="py-2 pr-3">Hra</th>
                <th className="py-2 pr-3 text-right">Aktivováno</th>
                <th className="py-2 pr-3 text-right">Tržba</th>
                <th className="py-2 text-right">{isAdmin ? 'Podíl partnera' : 'Váš podíl'}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={`${row.month}-${row.partnerName}-${row.gameTitle}`} className="border-b border-stone-200">
                  <td className="py-2 pr-3 whitespace-nowrap">{formatMonth(row.month)}</td>
                  {isAdmin && <td className="py-2 pr-3">{row.partnerName}</td>}
                  <td className="py-2 pr-3">{row.gameTitle}</td>
                  <td className="py-2 pr-3 text-right">{formatNumber(row.activatedCount)}</td>
                  <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(row.revenueCzk)}</td>
                  <td className="py-2 text-right whitespace-nowrap">{formatMoney(row.partnerShareCzk)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="font-bold">
              <tr>
                <td className="py-2 pr-3" colSpan={isAdmin ? 3 : 2}>
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
      <button type="button" onClick={() => void report.refetch()} className={`${linkButton} self-start`}>
        Obnovit
      </button>
    </Section>
  )
}
