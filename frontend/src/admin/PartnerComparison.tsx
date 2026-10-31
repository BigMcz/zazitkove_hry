import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type GameStock, type PartnerStats } from './adminApi.ts'
import { formatDays, formatMoney, formatNumber, formatShare } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, useLoggedOutOn401 } from './shared.ts'

/** Below this many days of cards a castle is listed as running out (printing and handing over takes time). */
export const LOW_STOCK_DAYS = 21

interface Column {
  label: string
  /** Value for sorting; null sorts last. */
  sortValue: (row: PartnerStats) => number | string | null
  render: (row: PartnerStats) => string
}

const soldShare = (row: PartnerStats) =>
  row.cardsDelivered === 0 ? null : (row.cardsDelivered - row.cardsInStock) / row.cardsDelivered

function columns(year: number): Column[] {
  return [
    { label: 'Hrad', sortValue: (row) => row.partnerName, render: (row) => row.partnerName },
    { label: `Aktivace ${year}`, sortValue: (row) => row.activatedInYear, render: (row) => formatNumber(row.activatedInYear) },
    { label: 'Tržba', sortValue: (row) => row.revenueCzk, render: (row) => formatMoney(row.revenueCzk) },
    { label: 'Podíl hradu', sortValue: (row) => row.partnerShareCzk, render: (row) => formatMoney(row.partnerShareCzk) },
    { label: 'Zisk', sortValue: (row) => row.profitCzk, render: (row) => formatMoney(row.profitCzk) },
    {
      label: 'U hradu',
      sortValue: (row) => row.cardsInStock,
      render: (row) =>
        row.cardsInStock === row.cardsInStockComputed
          ? formatNumber(row.cardsInStock)
          : `${formatNumber(row.cardsInStock)} z ${formatNumber(row.cardsInStockComputed)}`,
    },
    {
      label: 'Prodáno z předaných',
      sortValue: soldShare,
      render: (row) => formatShare(row.cardsDelivered - row.cardsInStock, row.cardsDelivered),
    },
    { label: 'Vydrží', sortValue: (row) => row.stockDays, render: stockLasts },
  ]
}

/** For the castle: the game whose cards run out first. */
function stockLasts(row: PartnerStats | GameStock): string {
  if (row.stockDays !== null) {
    return `asi ${formatDays(row.stockDays)}`
  }
  return row.cardsInStock > 0 ? 'bez prodeje' : '—'
}

/** Cards of the game for less than LOW_STOCK_DAYS at the current pace, or none left after earlier deliveries. */
function isRunningOut(game: GameStock): boolean {
  return (game.stockDays !== null && game.stockDays < LOW_STOCK_DAYS) || (game.cardsDelivered > 0 && game.cardsInStock === 0)
}

/** Games running out, named by the castle (and the game when the castle has more of them). */
function runningOutGames(rows: PartnerStats[]): string[] {
  return rows.flatMap((row) =>
    row.games.filter(isRunningOut).map((game) => {
      const name = row.games.length > 1 ? `${row.partnerName}, ${game.gameTitle}` : row.partnerName
      return `${name} (${game.cardsInStock === 0 ? 'došly' : `${formatNumber(game.cardsInStock)} ks, ${stockLasts(game)}`})`
    }),
  )
}

/** All castles side by side for the operator, with a warning for the castles running out of cards. */
export default function PartnerComparison({ year }: { year: number }) {
  const stats = useQuery({ queryKey: adminKeys.partnerStats(year), queryFn: () => adminApi.partnerStats(year) })
  useLoggedOutOn401(stats.error)
  const [sort, setSort] = useState<{ column: number; descending: boolean }>({ column: 1, descending: true })

  const cols = columns(year)
  const rows = [...(stats.data ?? [])].sort((a, b) => compare(cols[sort.column].sortValue(a), cols[sort.column].sortValue(b), sort.descending))
  const runningOut = runningOutGames(rows)

  return (
    <Section title={`Srovnání hradů ${year}`}>
      {runningOut.length > 0 && (
        <ErrorBanner
          tone="info"
          message={`Docházejí kartičky: ${runningOut.join(', ')}.`}
        />
      )}
      {stats.isPending && <p role="status">Načítám…</p>}
      {stats.isError && <ErrorBanner message={stats.error.message} onRetry={() => void stats.refetch()} />}
      {rows.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                {cols.map((column, index) => (
                  <th
                    key={column.label}
                    aria-sort={sort.column === index ? (sort.descending ? 'descending' : 'ascending') : undefined}
                    className={`py-2 pr-3 ${index > 0 ? 'text-right' : ''}`}
                  >
                    <button
                      type="button"
                      onClick={() =>
                        setSort((current) => ({
                          column: index,
                          // a new column starts from the largest value, names from A
                          descending: current.column === index ? !current.descending : index > 0,
                        }))
                      }
                      className="min-h-11 font-semibold whitespace-nowrap hover:underline"
                    >
                      {column.label}
                      {sort.column === index && (sort.descending ? ' ↓' : ' ↑')}
                    </button>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.partnerId} className="border-b border-stone-200">
                  <td className="py-2 pr-3">
                    <Link to={`/admin/partneri/${row.partnerId}`} className="font-semibold underline underline-offset-4">
                      {row.partnerName}
                    </Link>
                  </td>
                  {cols.slice(1).map((column) => (
                    <td
                      key={column.label}
                      className={`py-2 pr-3 text-right whitespace-nowrap ${
                        column.label === 'Vydrží' && row.games.some(isRunningOut) ? 'font-bold text-amber-800' : ''
                      }`}
                    >
                      {column.render(row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="text-sm text-stone-600">
        „U hradu“ vychází z poslední inventury hradu, pokud ji zadal; „z N“ je počet podle aktivací. „Vydrží“ platí pro hru,
        které kartičky dojdou nejdřív, s tempem prodeje za posledních 28 dní bez ohledu na zvolený rok. Vývojové kódy se nepočítají.
      </p>
    </Section>
  )
}

function compare(a: number | string | null, b: number | string | null, descending: boolean): number {
  if (a === null || b === null) {
    return a === b ? 0 : a === null ? 1 : -1
  }
  const result = typeof a === 'string' ? a.localeCompare(String(b), 'cs') : a - Number(b)
  return descending ? -result : result
}
