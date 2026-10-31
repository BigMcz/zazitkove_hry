import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { adminApi } from './adminApi.ts'
import Dialog from './Dialog.tsx'
import { formatPercent } from './format.ts'
import PartnerForm from './PartnerForm.tsx'
import { adminKeys, useLoggedOutOn401 } from './shared.ts'

const item = ({ isActive }: { isActive: boolean }) =>
  `flex min-h-11 flex-col justify-center rounded-lg px-3 py-1.5 ${
    isActive ? 'bg-night text-white [&>span:first-child]:text-lime' : 'text-stone-800 hover:bg-stone-200'
  }`

/** Left menu: the dashboard, adding a partner and one item per castle (partner). */
export default function PartnerNav() {
  const partners = useQuery({ queryKey: adminKeys.partners, queryFn: adminApi.partners })
  useLoggedOutOn401(partners.error)

  return (
    <nav aria-label="Hrady" className="flex flex-col gap-1">
      <NavLink to="/admin" end className={item}>
        <span className="font-semibold">Přehled</span>
        <span className="text-xs opacity-75">všechny hrady</span>
      </NavLink>
      <div className="mt-4 flex items-center justify-between gap-2 px-3">
        <h2 className="text-xs font-bold tracking-wide text-stone-500 uppercase">Hrady</h2>
        <AddPartnerButton />
      </div>
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

/** Opens the new partner form in a dialog; after saving continues to the partner's share. */
function AddPartnerButton() {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="min-h-11 rounded-lg px-2 text-sm font-semibold text-stone-800 hover:bg-stone-200"
      >
        + Přidat partnera
      </button>
      {open && (
        <Dialog title="Nový partner" wide onClose={() => setOpen(false)}>
          <PartnerForm
            onSaved={(partner) => {
              setOpen(false)
              navigate(`/admin/partneri/${partner.id}?sekce=podily`)
            }}
          />
        </Dialog>
      )}
    </>
  )
}
