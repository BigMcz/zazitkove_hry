import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type BillingAmounts, type MonthBilling } from './adminApi.ts'
import {
  addMonths,
  firstOfNextMonth,
  formatDate,
  formatMoney,
  formatMonth,
  formatNumber,
  formatPrice,
  todayInPrague,
} from './format.ts'
import Section from './Section.tsx'
import { adminKeys, useLoggedOutOn401 } from './shared.ts'

const arrow =
  'inline-flex size-11 items-center justify-center rounded-full border-2 border-stone-400 text-xl font-bold ' +
  'hover:border-stone-900 disabled:cursor-not-allowed disabled:opacity-30'

/**
 * What the operator invoices to the castle for a month: per game the activations, price, revenue, the castle's share
 * and the operator's part, which is the amount to invoice. A month is invoiced after it ended; the operator marks it
 * with the day of the invoice. Opens on the last ended month.
 */
export default function Billing({ partnerId }: { partnerId: string }) {
  const thisMonth = todayInPrague().slice(0, 7)
  const [month, setMonth] = useState(() => addMonths(thisMonth, -1))
  const billing = useQuery({
    queryKey: adminKeys.billing(partnerId, month),
    queryFn: () => adminApi.billing(partnerId, month),
  })
  useLoggedOutOn401(billing.error)
  const others = billing.data?.uninvoicedMonths.filter((uninvoiced) => uninvoiced !== month) ?? []

  return (
    <Section title="Fakturace">
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          aria-label="Předchozí měsíc"
          onClick={() => setMonth(addMonths(month, -1))}
          className={arrow}
        >
          ←
        </button>
        <h3 aria-live="polite" className="min-w-36 text-center text-lg font-bold">
          {formatMonth(month)}
        </h3>
        <button
          type="button"
          aria-label="Další měsíc"
          disabled={month >= thisMonth}
          onClick={() => setMonth(addMonths(month, 1))}
          className={arrow}
        >
          →
        </button>
        {billing.data && <Status billing={billing.data} />}
      </div>
      {others.length > 0 && (
        <p className="text-sm text-amber-900">
          Nevyfakturováno:{' '}
          {others.map((uninvoiced, index) => (
            <span key={uninvoiced}>
              {index > 0 && ', '}
              <button type="button" onClick={() => setMonth(uninvoiced)} className="font-semibold underline">
                {formatMonth(uninvoiced)}
              </button>
            </span>
          ))}
        </p>
      )}

      {billing.isPending && <p role="status">Načítám…</p>}
      {billing.isError && <ErrorBanner message={billing.error.message} onRetry={() => void billing.refetch()} />}
      {billing.data && (
        <>
          <BillingTable billing={billing.data} />
          <p className="text-lg">
            K fakturaci hradu za {formatMonth(month)}:{' '}
            <strong className="text-2xl">{formatMoney(billing.data.total.operatorCzk)}</strong>
          </p>
          <p className="text-sm text-stone-600">
            Hrad prodá kartičky a ponechá si svůj podíl, fakturuje se tržba pro provozovatele. Každý kód se počítá cenou
            hry a podílem hradu platnými v den aktivace, vývojové kódy se nepočítají.
          </p>
          {/* key: the date and errors start empty in each month */}
          <InvoiceMark key={month} partnerId={partnerId} billing={billing.data} />
        </>
      )}
    </Section>
  )
}

function Status({ billing }: { billing: MonthBilling }) {
  if (billing.invoice) {
    return <span className="rounded-full bg-emerald-100 px-3 py-1 text-sm font-semibold text-emerald-900">vyfakturováno</span>
  }
  if (!billing.ended) {
    return <span className="rounded-full bg-stone-100 px-3 py-1 text-sm font-semibold text-stone-700">probíhá</span>
  }
  return <span className="rounded-full bg-amber-100 px-3 py-1 text-sm font-semibold text-amber-900">k fakturaci</span>
}

function BillingTable({ billing }: { billing: MonthBilling }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Fakturace za {formatMonth(billing.month)} po hrách</caption>
        <thead className="border-b border-stone-300 text-stone-700">
          <tr>
            <th className="py-2 pr-3">Hra</th>
            <th className="py-2 pr-3 text-right">Aktivace</th>
            <th className="py-2 pr-3 text-right">Cena</th>
            <th className="py-2 pr-3 text-right">Tržba</th>
            <th className="py-2 pr-3 text-right">Podíl hradu</th>
            <th className="py-2 text-right">Tržba pro mě</th>
          </tr>
        </thead>
        <tbody>
          {billing.games.map((game) => (
            <tr key={game.gameId} className="border-b border-stone-200">
              <td className="py-2 pr-3 font-semibold">{game.gameTitle}</td>
              <td className="py-2 pr-3 text-right">{formatNumber(game.amounts.activatedCount)}</td>
              <td className="py-2 pr-3 text-right whitespace-nowrap">
                {game.prices.length === 0 ? (
                  <span className="font-semibold text-amber-900">bez ceny</span>
                ) : (
                  game.prices.map(formatPrice).join(' / ')
                )}
              </td>
              <Amounts amounts={game.amounts} />
            </tr>
          ))}
        </tbody>
        <tfoot className="font-bold">
          <tr>
            <td className="py-2 pr-3">Celkem</td>
            <td className="py-2 pr-3 text-right">{formatNumber(billing.total.activatedCount)}</td>
            <td className="py-2 pr-3" />
            <Amounts amounts={billing.total} />
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

function Amounts({ amounts }: { amounts: BillingAmounts }) {
  return (
    <>
      <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(amounts.revenueCzk)}</td>
      <td className="py-2 pr-3 text-right whitespace-nowrap">{formatMoney(amounts.partnerShareCzk)}</td>
      <td className="py-2 text-right whitespace-nowrap">{formatMoney(amounts.operatorCzk)}</td>
    </>
  )
}

/** The tick "vyfakturováno" with the day of the invoice; possible only after the month ended. */
function InvoiceMark({ partnerId, billing }: { partnerId: string; billing: MonthBilling }) {
  const queryClient = useQueryClient()
  const today = todayInPrague()
  const earliest = firstOfNextMonth(`${billing.month}-01`)
  const [invoicedOn, setInvoicedOn] = useState(today)
  const update = (data: MonthBilling) => queryClient.setQueryData(adminKeys.billing(partnerId, billing.month), data)
  const mark = useMutation({
    mutationFn: () => adminApi.markInvoiced(partnerId, billing.month, invoicedOn),
    onSuccess: update,
  })
  const unmark = useMutation({
    mutationFn: () => adminApi.unmarkInvoiced(partnerId, billing.month),
    onSuccess: update,
  })
  useLoggedOutOn401(mark.error ?? unmark.error)
  const error = mark.error ?? unmark.error

  if (!billing.ended) {
    return <p className="text-stone-700">Měsíc ještě neskončil, fakturovat půjde od {formatDate(earliest)}.</p>
  }

  const { invoice } = billing
  return (
    <div className="flex flex-col gap-2 rounded-xl border border-stone-300 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex min-h-11 items-center gap-2 font-semibold">
          <input
            type="checkbox"
            checked={invoice !== null}
            disabled={mark.isPending || unmark.isPending}
            onChange={(event) => (event.target.checked ? mark.mutate() : unmark.mutate())}
            className="size-5 accent-emerald-700"
          />
          Vyfakturováno
        </label>
        {invoice ? (
          <span className="text-stone-800">
            dne <strong>{formatDate(invoice.invoicedOn)}</strong> na {formatMoney(invoice.amountCzk)}
          </span>
        ) : (
          <label className="flex items-center gap-2">
            dne
            <input
              type="date"
              min={earliest}
              max={today}
              value={invoicedOn}
              onChange={(event) => setInvoicedOn(event.target.value)}
              className="min-h-11 rounded-lg border-2 border-stone-400 bg-white px-2"
            />
          </label>
        )}
      </div>
      {invoice && invoice.amountCzk !== billing.total.operatorCzk && (
        <p role="note" className="text-amber-900">
          Od fakturace se částka změnila (cena nebo podíl s datem v minulosti), dnes vychází{' '}
          {formatMoney(billing.total.operatorCzk)}. Označení zrušíte odškrtnutím.
        </p>
      )}
      {error && <ErrorBanner message={error.message} />}
    </div>
  )
}
