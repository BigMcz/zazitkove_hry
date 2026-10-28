import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { partnerApi } from './adminApi.ts'
import BatchList from './BatchList.tsx'
import { formatDate, formatPercent, todayInPrague } from './format.ts'
import MonthlyReport from './MonthlyReport.tsx'
import StatCards from './StatCards.tsx'
import YearSelect from './YearSelect.tsx'
import { adminKeys, thisYear } from './shared.ts'

/** What castle staff see: stock of cards, deliveries and monthly activations of their castle only. */
export default function CastleOverview() {
  const [year, setYear] = useState(thisYear)

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <OwnShares />
        <YearSelect year={year} onChange={setYear} />
      </div>
      <StatCards scope="partner" year={year} />
      <BatchList scope="partner" />
      <MonthlyReport scope="partner" year={year} />
    </>
  )
}

/** The castle's current share and a planned change, if any. */
function OwnShares() {
  const shares = useQuery({ queryKey: adminKeys.ownShares, queryFn: partnerApi.shares })
  if (!shares.data || shares.data.length === 0) {
    return <span />
  }
  const today = todayInPrague()
  const current = shares.data.filter((share) => share.validFrom <= today).at(-1)
  const planned = shares.data.filter((share) => share.validFrom > today)
  return (
    <p className="text-stone-800">
      {current && (
        <>
          Váš podíl z tržby: <strong>{formatPercent(current.sharePct)}</strong> (od {formatDate(current.validFrom)})
        </>
      )}
      {planned.map((share) => (
        <span key={share.id}>
          {' '}
          · od {formatDate(share.validFrom)}: <strong>{formatPercent(share.sharePct)}</strong>
        </span>
      ))}
    </p>
  )
}
