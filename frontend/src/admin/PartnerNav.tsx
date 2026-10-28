import { useQuery } from '@tanstack/react-query'
import { NavLink } from 'react-router'
import { adminApi } from './adminApi.ts'
import { formatPercent } from './format.ts'
import { adminKeys, useLoggedOutOn401 } from './shared.ts'

const item = ({ isActive }: { isActive: boolean }) =>
  `flex min-h-11 flex-col justify-center rounded-lg px-3 py-1.5 ${
    isActive ? 'bg-stone-900 text-white' : 'text-stone-800 hover:bg-stone-200'
  }`

/** Left menu: the dashboard and one item per castle (partner). */
export default function PartnerNav() {
  const partners = useQuery({ queryKey: adminKeys.partners, queryFn: adminApi.partners })
  useLoggedOutOn401(partners.error)

  return (
    <nav aria-label="Hrady" className="flex flex-col gap-1">
      <NavLink to="/admin" end className={item}>
        <span className="font-semibold">Přehled</span>
        <span className="text-xs opacity-75">všechny hrady</span>
      </NavLink>
      <h2 className="mt-4 px-3 text-xs font-bold tracking-wide text-stone-500 uppercase">Hrady</h2>
      {partners.isPending && <p className="px-3 text-sm">Načítám…</p>}
      {partners.data?.length === 0 && <p className="px-3 text-sm text-stone-600">Zatím žádný partner.</p>}
      {partners.data?.map((partner) => (
        <NavLink key={partner.id} to={`/admin/partneri/${partner.id}`} className={item}>
          <span className="font-semibold">{partner.name}</span>
          <span className="text-xs opacity-75">
            {partner.currentSharePct === null ? 'bez podílu' : `podíl ${formatPercent(partner.currentSharePct)}`}
          </span>
        </NavLink>
      ))}
    </nav>
  )
}
