import { useState } from 'react'
import GameplayStats from './GameplayStats.tsx'
import PartnerComparison from './PartnerComparison.tsx'
import StatCards from './StatCards.tsx'
import YearSelect from './YearSelect.tsx'
import { thisYear } from './shared.ts'

/** Operator's home: key figures and comparison of all castles and how the games are played. */
export default function Dashboard() {
  const [year, setYear] = useState(thisYear)

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl font-bold">Všechny hrady</h2>
        <YearSelect year={year} onChange={setYear} />
      </div>
      <StatCards scope="admin" year={year} />
      <PartnerComparison year={year} />
      <GameplayStats year={year} />
    </>
  )
}
