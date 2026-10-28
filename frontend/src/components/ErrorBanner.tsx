interface ErrorBannerProps {
  message: string
  tone?: 'error' | 'info'
  onRetry?: () => void
}

export default function ErrorBanner({ message, tone = 'error', onRetry }: ErrorBannerProps) {
  const colors =
    tone === 'error' ? 'border-red-700 bg-red-50 text-red-900' : 'border-amber-600 bg-amber-50 text-stone-900'
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={`rounded-xl border-l-4 p-4 text-base ${colors}`}>
      <p>{message}</p>
      {onRetry && (
        <button type="button" onClick={onRetry} className="mt-2 min-h-11 font-semibold underline underline-offset-4">
          Zkusit znovu
        </button>
      )}
    </div>
  )
}
