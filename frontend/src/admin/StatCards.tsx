import { useQuery } from '@tanstack/react-query'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, partnerApi, type Summary } from './adminApi.ts'
import { formatDateTime, formatMoney, formatNumber } from './format.ts'
import StockCountButton from './StockCountButton.tsx'
import { adminKeys, useLoggedOutOn401, type Scope } from './shared.ts'

interface StatCardsProps {
  scope: Scope
  year: number
  /** Operator: only this partner; all partners when missing. */
  partnerId?: string
}

/** Dashboard row of key figures: activations, money (and the operator's profit) and cards by state. */
export default function StatCards({ scope, year, partnerId }: StatCardsProps) {
  const isAdmin = scope === 'admin'
  const summary = useQuery({
    queryKey: adminKeys.summary(scope, year, partnerId),
    queryFn: () => (isAdmin ? adminApi.dashboard(year, partnerId) : partnerApi.summary(year)),
  })
  useLoggedOutOn401(summary.error)

  if (summary.isError) {
    return <ErrorBanner message={summary.error.message} onRetry={() => void summary.refetch()} />
  }
  const data = summary.data
  return (
    <div aria-busy={summary.isPending} className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {cards(isAdmin, year, data).map((card) => (
        <div
          key={card.label}
          className={`flex flex-col gap-1 rounded-2xl border p-4 shadow-sm ${
            card.highlight ? 'border-night bg-night text-white' : 'border-stone-300 bg-white'
          }`}
        >
          <span className={`text-sm font-semibold ${card.highlight ? 'text-stone-300' : 'text-stone-600'}`}>
            {card.label}
          </span>
          <span className={`text-2xl font-bold tabular-nums ${card.highlight ? 'text-lime' : ''}`}>
            {data ? card.value : '…'}
          </span>
          {card.hint && (
            <span className={`text-xs ${card.highlight ? 'text-stone-300' : 'text-stone-500'}`}>{card.hint}</span>
          )}
          {card.stockCount && data && data.games.length > 0 && (
            <span className="text-white [&_button]:text-white [&_button:hover]:text-lime">
              <StockCountButton year={year} summary={data} />
            </span>
          )}
        </div>
      ))}
    </div>
  )
}

interface Card {
  label: string
  value: string
  hint?: string
  highlight?: boolean
  /** The castle can correct this figure by its own stock count. */
  stockCount?: boolean
}

function cards(isAdmin: boolean, year: number, data: Summary | undefined): Card[] {
  const value = (pick: (summary: Summary) => string) => (data ? pick(data) : '')
  const activations: Card[] = [
    { label: 'Aktivace tento měsíc', value: value((s) => formatNumber(s.activatedThisMonth)) },
    { label: `Aktivace ${year}`, value: value((s) => formatNumber(s.activatedInYear)) },
  ]
  if (!isAdmin) {
    return [
      {
        label: 'Kartiček na skladě',
        value: value((s) => formatNumber(s.cardsInStock)),
        hint: [
          data && data.games.length > 1
            ? data.games.map((game) => `${game.gameTitle} ${formatNumber(game.cardsInStock)}`).join(' · ')
            : null,
          data?.stockCountedAt
            ? `podle vaší inventury z ${formatDateTime(data.stockCountedAt)}`
            : 'převzaté a dosud neaktivované',
        ]
          .filter(Boolean)
          .join(', '),
        highlight: true,
        stockCount: true,
      },
      { label: 'Převzato celkem', value: value((s) => formatNumber(s.cardsDelivered)), hint: 'kartiček' },
      ...activations,
      { label: `Tržba ${year}`, value: value((s) => formatMoney(s.revenueCzk)) },
      { label: `Váš podíl ${year}`, value: value((s) => formatMoney(s.partnerShareCzk)), highlight: true },
    ]
  }
  return [
    activations[0],
    { label: 'Tržba tento měsíc', value: value((s) => formatMoney(s.revenueThisMonthCzk)) },
    { label: 'Podíl hradů tento měsíc', value: value((s) => formatMoney(s.partnerShareThisMonthCzk)) },
    {
      label: 'Zisk tento měsíc',
      value: value((s) => formatMoney(s.profitThisMonthCzk ?? null)),
      hint: 'tržba − podíl hradů',
      highlight: true,
    },
    activations[1],
    { label: `Tržba ${year}`, value: value((s) => formatMoney(s.revenueCzk)) },
    { label: `Zisk ${year}`, value: value((s) => formatMoney(s.profitCzk ?? null)), hint: 'tržba − podíl hradů', highlight: true },
    { label: `Podíl hradů ${year}`, value: value((s) => formatMoney(s.partnerShareCzk)) },
    {
      label: 'Kartiček u hradů',
      value: value((s) => formatNumber(s.cardsInStock)),
      hint:
        data && data.cardsInStock !== data.cardsInStockComputed
          ? `podle inventur hradů, z aktivací ${formatNumber(data.cardsInStockComputed)}`
          : 'předané a neaktivované',
    },
    { label: 'Ve výrobě', value: value((s) => formatNumber(s.cardsInProduction)), hint: 'zatím nepředané' },
    { label: 'Předáno celkem', value: value((s) => formatNumber(s.cardsDelivered)), hint: 'kartiček' },
  ]
}
