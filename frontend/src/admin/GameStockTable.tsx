import { useQuery } from '@tanstack/react-query'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type GameStock } from './adminApi.ts'
import { addDays, formatDate, formatDateTime, formatNumber, todayInPrague } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, useLoggedOutOn401 } from './shared.ts'
import { isRunningOut, stockLasts } from './stock.ts'

/** Cards of each game of one castle: how many it can still sell and when they run out. */
export default function GameStockTable({ year, partnerId }: { year: number; partnerId: string }) {
  // The same query as the key figures above, so it is loaded only once
  const summary = useQuery({
    queryKey: adminKeys.summary('admin', year, partnerId),
    queryFn: () => adminApi.dashboard(year, partnerId),
  })
  useLoggedOutOn401(summary.error)
  const games = summary.data?.games ?? []

  return (
    <Section title="Kartičky po hrách">
      {summary.isPending && <p role="status">Načítám…</p>}
      {summary.isError && <ErrorBanner message={summary.error.message} onRetry={() => void summary.refetch()} />}
      {summary.isSuccess && games.length === 0 && <p className="text-stone-700">Hrad zatím nemá žádné kartičky.</p>}
      {games.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-stone-300 text-stone-700">
              <tr>
                <th className="py-2 pr-3 font-semibold">Hra</th>
                <th className="py-2 pr-3 text-right font-semibold">K prodeji</th>
                <th className="py-2 pr-3 text-right font-semibold">Ve výrobě</th>
                <th className="py-2 pr-3 text-right font-semibold">Prodáno za 28 dní</th>
                <th className="py-2 pr-3 text-right font-semibold">Vydrží</th>
                <th className="py-2 pr-3 text-right font-semibold">Dojdou asi</th>
              </tr>
            </thead>
            <tbody>
              {games.map((game) => {
                const low = isRunningOut(game)
                const warn = low ? 'font-bold text-amber-800' : ''
                return (
                  <tr key={game.gameId} className={`border-b border-stone-200 ${low ? 'bg-amber-50' : ''}`}>
                    <td className="py-2 pr-3 font-semibold">{game.gameTitle}</td>
                    <td className="py-2 pr-3 text-right whitespace-nowrap tabular-nums">
                      {formatNumber(game.cardsInStock)}
                      {game.stockCountedAt && (
                        <span className="block text-xs text-stone-600">
                          inventura {formatDateTime(game.stockCountedAt)}
                        </span>
                      )}
                    </td>
                    <td className="py-2 pr-3 text-right tabular-nums">{formatNumber(game.cardsInProduction)}</td>
                    <td className="py-2 pr-3 text-right tabular-nums">{formatNumber(game.activatedLast28Days)}</td>
                    <td className={`py-2 pr-3 text-right whitespace-nowrap ${warn}`}>
                      {game.cardsDelivered > 0 && game.cardsInStock === 0 ? 'došly' : stockLasts(game)}
                    </td>
                    <td className={`py-2 pr-3 text-right whitespace-nowrap ${warn}`}>{runsOutOn(game)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      <p className="text-sm text-stone-600">
        „K prodeji“ jsou předané a neaktivované kartičky, po inventuře hradu podle ní. Odhad počítá s tempem prodeje za
        posledních 28 dní bez ohledu na zvolený rok, kartičky ve výrobě v něm nejsou. Zvýrazněné hry vydrží méně než tři
        týdny.
      </p>
    </Section>
  )
}

function runsOutOn(game: GameStock): string {
  return game.stockDays === null ? '—' : formatDate(addDays(todayInPrague(), game.stockDays))
}
