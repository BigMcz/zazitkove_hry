import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type Partner } from './adminApi.ts'
import BatchList from './BatchList.tsx'
import { formatPercent } from './format.ts'
import MonthlyReport from './MonthlyReport.tsx'
import NewBatchForm from './NewBatchForm.tsx'
import PartnerForm from './PartnerForm.tsx'
import PartnerUsers from './PartnerUsers.tsx'
import Section from './Section.tsx'
import ShareHistory from './ShareHistory.tsx'
import StatCards from './StatCards.tsx'
import Tabs, { type Tab } from './Tabs.tsx'
import YearSelect from './YearSelect.tsx'
import { adminKeys, linkButton, thisYear, useLoggedOutOn401 } from './shared.ts'

type PartnerTab = 'prehled' | 'kody' | 'podily' | 'udaje' | 'ucty'

const tabs: Tab<PartnerTab>[] = [
  { id: 'prehled', label: 'Přehled' },
  { id: 'kody', label: 'Kódy a předání' },
  { id: 'podily', label: 'Podíl' },
  { id: 'udaje', label: 'Údaje firmy' },
  { id: 'ucty', label: 'Přístupy' },
]

/** One castle (partner): only its data, in tabs. The tab is kept in the URL (?sekce=…). */
export default function PartnerDetail() {
  const { partnerId = '' } = useParams()
  const partner = useQuery({ queryKey: adminKeys.partner(partnerId), queryFn: () => adminApi.partner(partnerId) })
  useLoggedOutOn401(partner.error)

  if (partner.isPending) {
    return <p role="status">Načítám…</p>
  }
  if (partner.isError) {
    return (
      <>
        <ErrorBanner message={partner.error.message} onRetry={() => void partner.refetch()} />
        <Link to="/admin" className={linkButton}>
          Zpět na přehled
        </Link>
      </>
    )
  }
  // key: a different partner starts with its own state (year, forms)
  return <PartnerView key={partner.data.id} partner={partner.data} />
}

function PartnerView({ partner }: { partner: Partner }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const active = tabs.find((tab) => tab.id === searchParams.get('sekce'))?.id ?? 'prehled'
  const [year, setYear] = useState(thisYear)

  return (
    <>
      <header className="flex flex-col gap-1">
        <h2 className="text-2xl font-bold">{partner.name}</h2>
        <p className="text-sm text-stone-600">
          {[
            partner.companyId && `IČ ${partner.companyId}`,
            partner.contactPerson,
            partner.contactEmail,
            partner.phone,
            `podíl ${partner.currentSharePct === null ? 'nenastaven' : formatPercent(partner.currentSharePct)}`,
          ]
            .filter(Boolean)
            .join(' · ')}
        </p>
      </header>
      <Tabs tabs={tabs} active={active} onChange={(id) => setSearchParams({ sekce: id }, { replace: true })}>
        {active === 'prehled' && (
          <>
            <div className="flex justify-end">
              <YearSelect year={year} onChange={setYear} />
            </div>
            <StatCards scope="admin" year={year} partnerId={partner.id} />
            <MonthlyReport scope="admin" year={year} partnerId={partner.id} />
          </>
        )}
        {active === 'kody' && (
          <>
            <NewBatchForm partnerId={partner.id} />
            <BatchList scope="admin" partnerId={partner.id} />
          </>
        )}
        {active === 'podily' && <ShareHistory partner={partner} />}
        {active === 'udaje' && (
          <Section title="Údaje firmy">
            <PartnerForm partner={partner} />
          </Section>
        )}
        {active === 'ucty' && <PartnerUsers partnerId={partner.id} />}
      </Tabs>
    </>
  )
}
