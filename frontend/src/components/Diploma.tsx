import { useState } from 'react'
import { useGameTexts } from '../i18n/gameTexts.ts'
import { type DiplomaContent, saveDiploma } from './diplomaImage.ts'
import ErrorBanner from './ErrorBanner.tsx'
import { buttonSecondary } from './ui.ts'

/** Instead of times and penalties a game for children ends with a diploma to show, keep or print. */
export default function Diploma({ content }: { content: DiplomaContent }) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const texts = useGameTexts()

  async function handleSave() {
    setSaving(true)
    setError(null)
    try {
      await saveDiploma(content, texts)
    } catch {
      setError(texts.diplomaSaveFailed)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <section
        aria-labelledby="diploma-heading"
        className="flex flex-col items-center gap-2 rounded-2xl border-4 border-double border-amber-600 bg-amber-50 p-4 pb-6 text-center"
      >
        {content.imageUrl && (
          <img src={content.imageUrl} alt="" className="mb-2 aspect-[4/3] w-full rounded-xl object-cover" />
        )}
        <h2 id="diploma-heading" className="font-serif text-4xl font-bold tracking-wide text-stone-950">
          {texts.diploma}
        </h2>
        {content.title && (
          <p className="text-sm font-bold tracking-[0.15em] text-amber-800 uppercase">{content.title}</p>
        )}
        {content.name && <p className="mt-2 font-serif text-3xl font-bold text-amber-900">{content.name}</p>}
        <p className="mt-1 text-lg text-stone-900">
          {texts.diplomaFor(content.tasks)} <br />
          {texts.inGame} <strong>{content.gameTitle}</strong>
        </p>
        <p className="text-sm text-stone-700">{new Date(content.playedAt).toLocaleDateString(texts.locale)}</p>
      </section>
      <button type="button" onClick={() => void handleSave()} disabled={saving} className={buttonSecondary}>
        {saving ? texts.savingDiploma : texts.saveDiploma}
      </button>
      {error && <ErrorBanner message={error} />}
    </div>
  )
}
