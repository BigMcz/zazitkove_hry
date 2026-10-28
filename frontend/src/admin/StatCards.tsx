import { useQuery } from '@tanstack/react-query'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, partnerApi, type Summary } from './adminApi.ts'
import { formatMoney, formatNumber } from './format.ts'
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
            card.highlight ? 'border-stone-900 bg-stone-900 text-white' : 'border-stone-300 bg-white'
          }`}
        >
          <span className={`text-sm font-semibold ${card.highlight ? 'text-stone-300' : 'text-stone-600'}`}>
            {card.label}
          </span>
          <span className="text-2xl font-bold tabular-nums">{data ? card.value : '…'}</span>
          {card.hint && (
            <span className={`text-xs ${card.highlight ? 'text-stone-300' : 'text-stone-500'}`}>{card.hint}</span>
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
        hint: 'převzaté a dosud neaktivované',
        highlight: true,
      },
      { label: 'Převzato celkem', value: value((s) => formatNumber(s.cardsDelivered)), hint: 'kartiček' },
      ...activations,
      { label: `Tržba ${year}`, value: value((s) => formatMoney(s.revenueCzk)) },
      { label: `Váš podíl ${year}`, value: value((s) => formatMoney(s.partnerShareCzk)), highlight: true },
    ]
  }
  return [
    ...activations,
    { label: `Tržba ${year}`, value: value((s) => formatMoney(s.revenueCzk)) },
    { label: `Zisk ${year}`, value: value((s) => formatMoney(s.profitCzk ?? null)), hint: 'tržba − podíl hradů', highlight: true },
    { label: `Podíl hradů ${year}`, value: value((s) => formatMoney(s.partnerShareCzk)) },
    {
      label: 'Kartiček u hradů',
      value: value((s) => formatNumber(s.cardsInStock)),
      hint: 'předané a neaktivované',
    },
    { label: 'Ve výrobě', value: value((s) => formatNumber(s.cardsInProduction)), hint: 'zatím nepředané' },
    { label: 'Předáno celkem', value: value((s) => formatNumber(s.cardsDelivered)), hint: 'kartiček' },
  ]
}
