import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { adminApi, type Partner, type PartnerRequest } from './adminApi.ts'
import { fieldLabel, invalidateAdminData, useLoggedOutOn401 } from './shared.ts'

interface PartnerFormProps {
  /** Edit this partner; a new partner is created when missing. */
  partner?: Partner
  onSaved?: (partner: Partner) => void
}

/** Company details of a castle (partner): name, IČ, DIČ and contact. */
export default function PartnerForm({ partner, onSaved }: PartnerFormProps) {
  const queryClient = useQueryClient()
  const [values, setValues] = useState<PartnerRequest>(() => ({
    name: partner?.name ?? '',
    companyId: partner?.companyId ?? '',
    vatId: partner?.vatId ?? '',
    contactEmail: partner?.contactEmail ?? '',
    contactPerson: partner?.contactPerson ?? '',
    phone: partner?.phone ?? '',
  }))
  const save = useMutation({
    mutationFn: (request: PartnerRequest) =>
      partner ? adminApi.updatePartner(partner.id, request) : adminApi.createPartner(request),
    onSuccess: async (saved) => {
      if (!partner) {
        setValues({ name: '', companyId: '', vatId: '', contactEmail: '', contactPerson: '', phone: '' })
      }
      await invalidateAdminData(queryClient)
      onSaved?.(saved)
    },
  })
  useLoggedOutOn401(save.error)

  const set = (field: keyof PartnerRequest) => (value: string) => setValues((current) => ({ ...current, [field]: value }))
  const orNull = (value: string | null) => (value?.trim() ? value.trim() : null)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    save.mutate({
      name: values.name.trim(),
      companyId: orNull(values.companyId),
      vatId: orNull(values.vatId),
      contactEmail: orNull(values.contactEmail),
      contactPerson: orNull(values.contactPerson),
      phone: orNull(values.phone),
    })
  }

  const field = (label: string, name: keyof PartnerRequest, props: Record<string, unknown> = {}) => (
    <label className={fieldLabel}>
      {label}
      <input
        value={values[name] ?? ''}
        onChange={(event) => set(name)(event.target.value)}
        className={input}
        autoComplete="off"
        {...props}
      />
    </label>
  )

  return (
    <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2">
      <div className="sm:col-span-2">{field('Název firmy', 'name', { required: true, maxLength: 200 })}</div>
      {field('IČ', 'companyId', { inputMode: 'numeric', pattern: '\\d{8}', title: '8 číslic', maxLength: 8 })}
      {field('DIČ', 'vatId', { placeholder: 'CZ12345678', maxLength: 14 })}
      {field('Kontaktní osoba', 'contactPerson', { maxLength: 200 })}
      {field('E-mail', 'contactEmail', { type: 'email', maxLength: 200 })}
      {field('Telefon', 'phone', { type: 'tel', maxLength: 30 })}
      {save.isError && (
        <div className="sm:col-span-2">
          <ErrorBanner message={save.error.message} />
        </div>
      )}
      {save.isSuccess && (
        <p role="status" className="font-semibold text-emerald-800 sm:col-span-2">
          {partner ? 'Údaje jsou uložené.' : `Partner ${save.data.name} je přidaný — vyberte ho vlevo a nastavte podíl.`}
        </p>
      )}
      <button type="submit" disabled={save.isPending} className={`${buttonPrimary} sm:col-span-2`}>
        {partner ? 'Uložit údaje' : 'Přidat partnera'}
      </button>
    </form>
  )
}
