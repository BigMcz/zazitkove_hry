import { useState } from 'react'
import ChangePasswordForm from './ChangePasswordForm.tsx'
import Dialog from './Dialog.tsx'

/** Small key icon in the header of the castle overview that opens the password change. */
export default function PasswordButton() {
  const [open, setOpen] = useState(false)

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Změnit heslo"
        title="Změnit heslo"
        className="inline-flex size-11 items-center justify-center rounded-full text-stone-300 hover:bg-night-card hover:text-lime"
      >
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <circle cx="8" cy="15" r="4" />
          <path d="M10.85 12.15 19 4M18 5l2 2M15 8l2 2" strokeLinecap="round" />
        </svg>
      </button>
      {open && (
        <Dialog title="Změna hesla" onClose={() => setOpen(false)}>
          <ChangePasswordForm />
        </Dialog>
      )}
    </>
  )
}
