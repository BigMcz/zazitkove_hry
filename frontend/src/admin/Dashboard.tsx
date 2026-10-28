import { useState } from 'react'
import { useNavigate } from 'react-router'
import PartnerForm from './PartnerForm.tsx'
import PartnerUsers from './PartnerUsers.tsx'
import Section from './Section.tsx'
import StatCards from './StatCards.tsx'
import YearSelect from './YearSelect.tsx'
import { thisYear } from './shared.ts'

/** Operator's home: key figures of all castles, adding a partner and the castles' accounts. */
export default function Dashboard() {
  const [year, setYear] = useState(thisYear)
  const navigate = useNavigate()

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl font-bold">Všechny hrady</h2>
        <YearSelect year={year} onChange={setYear} />
      </div>
      <StatCards scope="admin" year={year} />
      <Section title="Přidat partnera">
        <PartnerForm onSaved={(partner) => navigate(`/admin/partneri/${partner.id}?sekce=podily`)} />
      </Section>
      <PartnerUsers />
    </>
  )
}
