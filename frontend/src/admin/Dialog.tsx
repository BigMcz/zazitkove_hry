import { useEffect, useId, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

interface DialogProps {
  title: string
  onClose: () => void
  /** Width of the dialog; small forms use the narrow one. */
  wide?: boolean
  children: ReactNode
}

/** Modal dialog over the page; closes with the cross, Escape or a click outside. */
export default function Dialog({ title, onClose, wide = false, children }: DialogProps) {
  const titleId = useId()
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Portal: the dialog must not end up inside the markup of the button that opened it
  return createPortal(
    <div
      className="fixed inset-0 z-10 flex items-start justify-center overflow-y-auto bg-stone-950/40 px-4 py-20"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={`flex w-full flex-col gap-4 rounded-2xl bg-white p-5 shadow-xl ${wide ? 'max-w-2xl' : 'max-w-sm'}`}
      >
        <div className="flex items-center justify-between">
          <h2 id={titleId} className="text-xl font-bold">
            {title}
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
        {children}
      </div>
    </div>,
    document.body,
  )
}
