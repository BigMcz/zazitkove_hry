import { useOfflineStatus } from '../offline/gameDownload.ts'

/** Tells the player whether the game is already in the phone, so that losing the signal is no problem. */
export default function OfflineStatus({ token }: { token: string | null }) {
  const status = useOfflineStatus(token)
  if (!status) {
    return null
  }
  if (status.phase === 'downloading') {
    return (
      <p role="status" className="text-sm text-stone-600">
        Stahuji hru do telefonu pro hraní bez signálu…
        {status.total > 0 && ` ${status.done} z ${status.total}`}
      </p>
    )
  }
  return (
    <p role="status" className="text-sm font-semibold text-emerald-800">
      ✓ Hra je stažená, můžete hrát i bez signálu.
    </p>
  )
}
