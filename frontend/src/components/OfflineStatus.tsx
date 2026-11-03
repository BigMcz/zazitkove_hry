import { useGameTexts } from '../i18n/gameTexts.ts'
import { useOfflineStatus } from '../offline/gameDownload.ts'

/** Tells the player whether the game is already in the phone, so that losing the signal is no problem. */
export default function OfflineStatus({ token }: { token: string | null }) {
  const status = useOfflineStatus(token)
  const texts = useGameTexts()
  if (!status) {
    return null
  }
  if (status.phase === 'downloading') {
    return (
      <p role="status" className="text-sm text-stone-600">
        {texts.downloading}
        {status.total > 0 && texts.downloadProgress(status.done, status.total)}
      </p>
    )
  }
  return (
    <p role="status" className="text-sm font-semibold text-emerald-800">
      {texts.downloaded}
    </p>
  )
}
