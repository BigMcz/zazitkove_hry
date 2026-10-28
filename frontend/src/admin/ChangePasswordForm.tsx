import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { MIN_PASSWORD_LENGTH, partnerApi } from './adminApi.ts'
import { fieldLabel, useLoggedOutOn401 } from './shared.ts'

/** Castle staff change the initial password they got from the operator. */
export default function ChangePasswordForm() {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeated, setRepeated] = useState('')
  const change = useMutation({
    mutationFn: () => partnerApi.changePassword(current, next),
    onSuccess: () => {
      setCurrent('')
      setNext('')
      setRepeated('')
    },
  })
  useLoggedOutOn401(change.error)

  const mismatch = repeated !== '' && next !== repeated

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!mismatch) {
      change.mutate()
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <label className={fieldLabel}>
        Současné heslo
        <input
          type="password"
          required
          autoFocus
          autoComplete="current-password"
          value={current}
          onChange={(event) => setCurrent(event.target.value)}
          className={input}
        />
      </label>
      <label className={fieldLabel}>
        Nové heslo (min. {MIN_PASSWORD_LENGTH} znaků)
        <input
          type="password"
          required
          minLength={MIN_PASSWORD_LENGTH}
          maxLength={100}
          autoComplete="new-password"
          value={next}
          onChange={(event) => setNext(event.target.value)}
          className={input}
        />
      </label>
      <label className={fieldLabel}>
        Nové heslo znovu
        <input
          type="password"
          required
          autoComplete="new-password"
          value={repeated}
          onChange={(event) => setRepeated(event.target.value)}
          className={input}
        />
      </label>
      {mismatch && (
        <p role="alert" className="text-red-800">
          Nová hesla se neshodují.
        </p>
      )}
      {change.isError && <ErrorBanner message={change.error.message} />}
      {change.isSuccess && (
        <p role="status" className="font-semibold text-emerald-800">
          Heslo je změněné.
        </p>
      )}
      <button type="submit" disabled={change.isPending || mismatch} className={buttonPrimary}>
        Změnit heslo
      </button>
    </form>
  )
}
