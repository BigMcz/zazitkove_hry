import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import ChangePasswordForm from './ChangePasswordForm.tsx'

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
        className="inline-flex size-11 items-center justify-center rounded-full text-stone-700 hover:bg-stone-200 hover:text-stone-950"
      >
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <circle cx="8" cy="15" r="4" />
          <path d="M10.85 12.15 19 4M18 5l2 2M15 8l2 2" strokeLinecap="round" />
        </svg>
      </button>
      {open && <PasswordDialog onClose={() => setOpen(false)} />}
    </>
  )
}

function PasswordDialog({ onClose }: { onClose: () => void }) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Portal: the dialog must not end up inside the header's markup
  return createPortal(
    <div
      className="fixed inset-0 z-10 flex items-start justify-center bg-stone-950/40 px-4 pt-20"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="password-dialog-title"
        className="flex w-full max-w-sm flex-col gap-4 rounded-2xl bg-white p-5 shadow-xl"
      >
        <div className="flex items-center justify-between">
          <h2 id="password-dialog-title" className="text-xl font-bold">
            Změna hesla
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Zavřít"
            className="inline-flex size-11 items-center justify-center rounded-full text-2xl leading-none hover:bg-stone-100"
          >
            ×
          </button>
        </div>
        <ChangePasswordForm />
      </div>
    </div>,
    document.body,
  )
}
