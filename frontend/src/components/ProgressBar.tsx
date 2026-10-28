interface ProgressBarProps {
  /** Order of the station being played, 1-based. */
  current: number
  total: number
}

export default function ProgressBar({ current, total }: ProgressBarProps) {
  const percent = total > 0 ? Math.round((current / total) * 100) : 0
  return (
    <div className="flex flex-col gap-1.5">
      <p className="text-sm font-semibold tracking-wide text-stone-700 uppercase">
        Stanoviště {current} / {total}
      </p>
      <div
        role="progressbar"
        aria-label="Postup hrou"
        aria-valuemin={1}
        aria-valuemax={total}
        aria-valuenow={current}
        className="h-2.5 w-full overflow-hidden rounded-full bg-stone-300"
      >
        <div className="h-full rounded-full bg-amber-600" style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}
