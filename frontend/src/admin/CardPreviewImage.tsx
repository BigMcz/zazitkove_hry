import type { CardPreview } from './adminApi.ts'

interface CardPreviewImageProps {
  label: string
  preview: CardPreview | undefined
  loading: boolean
  error: Error | null
}

/** Rendered side of a card with the trim (magenta), the safe zone (dashed) and the lighter bleed that is cut off. */
export default function CardPreviewImage({ label, preview, loading, error }: CardPreviewImageProps) {
  return (
    <figure className="flex flex-col gap-2">
      <figcaption className="font-semibold">
        {label}
        {loading && <span className="ml-2 font-normal text-stone-600">překresluji…</span>}
      </figcaption>
      {preview ? (
        <img
          src={preview.image}
          alt={`Náhled: ${label.toLowerCase()}`}
          className="w-full max-w-md rounded border border-stone-300 bg-white shadow-sm"
        />
      ) : (
        !error && <div className="aspect-[96/56] w-full max-w-md animate-pulse rounded bg-stone-100" />
      )}
      {error && (
        <p role="alert" className="text-red-800">
          {error.message}
        </p>
      )}
      {preview && preview.warnings.length > 0 && (
        <ul className="flex list-disc flex-col gap-1 pl-5 text-sm text-amber-900">
          {preview.warnings.map((warning) => (
            <li key={warning}>{warning}</li>
          ))}
        </ul>
      )}
    </figure>
  )
}
