import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary } from '../components/ui.ts'
import {
  adminApi,
  type CardFile,
  type CardLayout,
  type CardSide,
  type CardTemplate,
  type CardTemplateRequest,
  type CodeAlign,
} from './adminApi.ts'
import { cardSizes, defaultLayout, sizeOf, type CardSizeId, type Orientation } from './cardLayout.ts'
import CardPreviewImage from './CardPreviewImage.tsx'
import { formatDateTime, formatNumber } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, fieldLabel, linkButton, useLoggedOutOn401 } from './shared.ts'

const field = 'min-h-11 w-24 rounded-lg border-2 border-stone-400 bg-white px-2'
const PREVIEW_DELAY_MS = 400

/**
 * Card template of a game: size, graphics of both sides and where the QR code, the code and the serial number go on
 * the back, with a live preview. Saving creates a new version; batches already printed keep theirs.
 */
export default function CardTemplateEditor() {
  const { gameId = '' } = useParams()
  const [searchParams] = useSearchParams()
  const batchId = searchParams.get('davka')
  const games = useQuery({ queryKey: adminKeys.games, queryFn: adminApi.games })
  const versions = useQuery({ queryKey: adminKeys.cardTemplates(gameId), queryFn: () => adminApi.cardTemplates(gameId) })
  useLoggedOutOn401(games.error ?? versions.error)
  const game = games.data?.find((candidate) => candidate.id === gameId)
  const [saved, setSaved] = useState<Saved | null>(null)

  return (
    <>
      <header className="flex flex-col gap-1">
        <Link
          to={batchId ? `/admin/batches/${batchId}/tisk` : game ? `/admin/partneri/${game.partnerId}?sekce=kody` : '/admin'}
          className={`${linkButton} self-start`}
        >
          {batchId ? 'Zpět na tisk dávky' : 'Zpět na kódy hradu'}
        </Link>
        <h2 className="text-2xl font-bold">Šablona kartičky{game && `: ${game.title}`}</h2>
        {game && <p className="text-sm text-stone-600">{game.partnerName}</p>}
      </header>
      {versions.isPending && <p role="status">Načítám…</p>}
      {versions.isError && <ErrorBanner message={versions.error.message} onRetry={() => void versions.refetch()} />}
      {saved && (
        <div role="status" className="flex flex-col gap-2 rounded-xl border border-emerald-300 bg-emerald-50 p-4">
          <p className="font-semibold text-emerald-900">Uložena verze {saved.version}.</p>
          {saved.warnings.length > 0 && (
            <ul className="flex list-disc flex-col gap-1 pl-5 text-amber-900">
              {saved.warnings.map((warning) => (
                <li key={warning}>{warning}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      {versions.data && (
        <>
          {/* key: after saving, the editor starts from the new version */}
          <Editor
            key={versions.data[0]?.id ?? 'new'}
            gameId={gameId}
            latest={versions.data[0] ?? null}
            onSaved={setSaved}
          />
          <Versions versions={versions.data} />
        </>
      )}
    </>
  )
}

/** Graphic of a side: a new upload, or the one of the latest version (null = none yet). */
type Graphic = { file: CardFile; uploaded: boolean } | null

interface Saved {
  version: number
  warnings: string[]
}

interface EditorProps {
  gameId: string
  latest: CardTemplate | null
  onSaved: (saved: Saved) => void
}

function Editor({ gameId, latest, onSaved }: EditorProps) {
  const queryClient = useQueryClient()
  const [layout, setLayout] = useState<CardLayout>(() => latest?.layout ?? defaultLayout('vizitka', 'landscape'))
  const [front, setFront] = useState<Graphic>(() => (latest ? { file: latest.front, uploaded: false } : null))
  const [back, setBack] = useState<Graphic>(() => (latest ? { file: latest.back, uploaded: false } : null))
  const [note, setNote] = useState('')

  const request: CardTemplateRequest = {
    layout,
    frontFileId: front?.uploaded ? front.file.id : null,
    backFileId: back?.uploaded ? back.file.id : null,
    note: note.trim() || null,
  }
  // The note does not change the picture
  const debounced = useDebounced({ ...request, note: null }, PREVIEW_DELAY_MS)

  const save = useMutation({
    mutationFn: () => adminApi.createCardTemplate(gameId, request),
    onSuccess: async (result) => {
      onSaved({ version: result.template.version, warnings: result.warnings })
      window.scrollTo({ top: 0 })
      await queryClient.invalidateQueries({ queryKey: adminKeys.cardTemplates(gameId) })
      // The print pages show the latest version
      await queryClient.invalidateQueries({ queryKey: ['admin', 'batch-print'] })
    },
  })
  useLoggedOutOn401(save.error)

  const update = (changes: Partial<CardLayout>) => setLayout((current) => ({ ...current, ...changes }))
  const size = sizeOf(layout)
  const changeSize = (sizeId: CardSizeId, orientation: Orientation) =>
    setLayout(defaultLayout(sizeId, orientation, layout.bleedMm))
  const serial = layout.serialXMm !== null && layout.serialYMm !== null && layout.serialFontPt !== null

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,32rem)_1fr]">
        <form onSubmit={handleSubmit} className="flex flex-col gap-6">
          <Section title="Rozměr">
            <div className="flex flex-wrap items-end gap-4">
              <label className={fieldLabel}>
                Formát
                <select
                  value={size?.sizeId ?? ''}
                  onChange={(event) => changeSize(event.target.value as CardSizeId, size?.orientation ?? 'landscape')}
                  className="min-h-11 rounded-lg border-2 border-stone-400 bg-white px-2"
                >
                  {!size && <option value="">{`${layout.widthMm} × ${layout.heightMm} mm`}</option>}
                  {cardSizes.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              <fieldset className="flex gap-4">
                <legend className="font-semibold">Orientace</legend>
                {(['landscape', 'portrait'] as const).map((orientation) => (
                  <label key={orientation} className="flex min-h-11 items-center gap-2">
                    <input
                      type="radio"
                      name="orientation"
                      checked={size?.orientation === orientation}
                      onChange={() => changeSize(size?.sizeId ?? 'vizitka', orientation)}
                    />
                    {orientation === 'landscape' ? 'na šířku' : 'na výšku'}
                  </label>
                ))}
              </fieldset>
              <NumberField label="Spadávka (mm)" value={layout.bleedMm} min={0} max={10} step={0.5} onChange={(bleedMm) => update({ bleedMm })} />
            </div>
            <p className="text-sm text-stone-600">
              Změna formátu nastaví výchozí rozložení. Spadávku upravte podle tiskárny, obvykle 2 až 3 mm.
            </p>
          </Section>

          <Section title="Grafika">
            <p className="text-sm text-stone-700">
              Nejlépe PDF od grafika o rozměru {formatNumber(layout.widthMm + 2 * layout.bleedMm)} ×{' '}
              {formatNumber(layout.heightMm + 2 * layout.bleedMm)} mm (ořez a spadávka). PNG nebo JPG ve stejném poměru
              stran a aspoň 300 DPI.
            </p>
            <FileField side="FRONT" graphic={front} latest={latest} onUploaded={setFront} />
            <FileField side="BACK" graphic={back} latest={latest} onUploaded={setBack} />
          </Section>

          <Section title="Zadní strana">
            <Group title="QR kód (včetně bílého okraje)">
              <NumberField label="x (mm)" value={layout.qrXMm} onChange={(qrXMm) => update({ qrXMm })} />
              <NumberField label="y (mm)" value={layout.qrYMm} onChange={(qrYMm) => update({ qrYMm })} />
              <NumberField label="Velikost (mm)" value={layout.qrSizeMm} min={18} onChange={(qrSizeMm) => update({ qrSizeMm })} />
              <label className="flex min-h-11 items-center gap-2 self-end">
                <input
                  type="checkbox"
                  checked={layout.qrWhiteBox}
                  onChange={(event) => update({ qrWhiteBox: event.target.checked })}
                />
                Bílý podklad
              </label>
            </Group>
            <Group title="Kód">
              <NumberField label="x (mm)" value={layout.codeXMm} onChange={(codeXMm) => update({ codeXMm })} />
              <NumberField label="y účaří (mm)" value={layout.codeYMm} onChange={(codeYMm) => update({ codeYMm })} />
              <NumberField label="Písmo (pt)" value={layout.codeFontPt} min={6} max={48} onChange={(codeFontPt) => update({ codeFontPt })} />
              <label className={fieldLabel}>
                Zarovnání
                <select
                  value={layout.codeAlign}
                  onChange={(event) => update({ codeAlign: event.target.value as CodeAlign })}
                  className="min-h-11 rounded-lg border-2 border-stone-400 bg-white px-2"
                >
                  <option value="LEFT">vlevo</option>
                  <option value="CENTER">na střed</option>
                  <option value="RIGHT">vpravo</option>
                </select>
              </label>
              <label className={fieldLabel}>
                Barva
                <input
                  type="color"
                  value={layout.codeColor}
                  onChange={(event) => update({ codeColor: event.target.value })}
                  className="h-11 w-16 rounded-lg border-2 border-stone-400 bg-white"
                />
              </label>
            </Group>
            <Group title="Pořadové číslo">
              <label className="flex min-h-11 items-center gap-2 self-end">
                <input
                  type="checkbox"
                  checked={serial}
                  onChange={(event) => {
                    const defaults = defaultLayout(size?.sizeId ?? 'vizitka', size?.orientation ?? 'landscape')
                    update(
                      event.target.checked
                        ? { serialXMm: defaults.serialXMm, serialYMm: defaults.serialYMm, serialFontPt: defaults.serialFontPt }
                        : { serialXMm: null, serialYMm: null, serialFontPt: null },
                    )
                  }}
                />
                Tisknout
              </label>
              {serial && (
                <>
                  <NumberField label="x (mm)" value={layout.serialXMm ?? 0} onChange={(serialXMm) => update({ serialXMm })} />
                  <NumberField label="y účaří (mm)" value={layout.serialYMm ?? 0} onChange={(serialYMm) => update({ serialYMm })} />
                  <NumberField label="Písmo (pt)" value={layout.serialFontPt ?? 7} min={4} max={24} onChange={(serialFontPt) => update({ serialFontPt })} />
                </>
              )}
            </Group>
            <p className="text-sm text-stone-600">
              Pozice se měří od levého horního rohu hotové kartičky (ořezu). Kód a pořadové číslo mají stejné zarovnání a
              barvu. Černá se tiskne jako čistá černá.
            </p>
          </Section>

          <label className={fieldLabel}>
            Poznámka k verzi
            <input
              value={note}
              maxLength={300}
              onChange={(event) => setNote(event.target.value)}
              placeholder="např. nová grafika od grafika"
              className="min-h-11 rounded-lg border-2 border-stone-400 bg-white px-2 font-normal"
            />
          </label>
          {save.isError && <ErrorBanner message={save.error.message} />}
          <button type="submit" disabled={save.isPending || !front || !back} className={buttonPrimary}>
            {latest ? `Uložit jako verzi ${latest.version + 1}` : 'Uložit šablonu'}
          </button>
          {(!front || !back) && <p className="text-sm text-stone-600">Pro uložení nahrajte grafiku obou stran.</p>}
        </form>

        <div className="flex flex-col gap-6 xl:sticky xl:top-4 xl:self-start">
          <Preview gameId={gameId} side="FRONT" request={debounced} />
          <Preview gameId={gameId} side="BACK" request={debounced} />
        </div>
    </div>
  )
}

function Preview({ gameId, side, request }: { gameId: string; side: CardSide; request: CardTemplateRequest }) {
  const preview = useQuery({
    queryKey: ['admin', 'card-preview', gameId, side, request],
    queryFn: () => adminApi.previewCardTemplate(gameId, side, request),
    // The last picture stays while the next one is drawn
    placeholderData: keepPreviousData,
    retry: false,
  })
  useLoggedOutOn401(preview.error)
  return (
    <CardPreviewImage
      label={side === 'FRONT' ? 'Přední strana' : 'Zadní strana s ukázkovým kódem'}
      preview={preview.data}
      loading={preview.isFetching}
      error={preview.error}
    />
  )
}

interface FileFieldProps {
  side: CardSide
  graphic: Graphic
  latest: CardTemplate | null
  onUploaded: (graphic: Graphic) => void
}

function FileField({ side, graphic, latest, onUploaded }: FileFieldProps) {
  const upload = useMutation({
    mutationFn: adminApi.uploadCardFile,
    onSuccess: (file) => onUploaded({ file, uploaded: true }),
  })
  useLoggedOutOn401(upload.error)
  const label = side === 'FRONT' ? 'Přední strana' : 'Zadní strana'

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) {
      upload.mutate(file)
    }
    event.target.value = ''
  }

  return (
    <div className="flex flex-col gap-1">
      <label className={fieldLabel}>
        {label}
        <input
          type="file"
          accept="application/pdf,image/png,image/jpeg"
          disabled={upload.isPending}
          onChange={handleChange}
          className="font-normal"
        />
      </label>
      <p className="text-sm text-stone-700">
        {upload.isPending
          ? 'Nahrávám…'
          : graphic
            ? `${graphic.file.fileName} (${formatSize(graphic.file.sizeBytes)})${graphic.uploaded ? ', nově nahraná' : `, z verze ${latest?.version}`}`
            : 'Zatím bez grafiky.'}
        {graphic?.uploaded && latest && (
          <button
            type="button"
            onClick={() => onUploaded({ file: side === 'FRONT' ? latest.front : latest.back, uploaded: false })}
            className={`${linkButton} ml-3 min-h-0`}
          >
            Ponechat z verze {latest.version}
          </button>
        )}
      </p>
      {upload.isError && (
        <p role="alert" className="text-red-800">
          {upload.error.message}
        </p>
      )}
    </div>
  )
}

function Versions({ versions }: { versions: CardTemplate[] }) {
  if (versions.length === 0) {
    return null
  }
  return (
    <Section title="Verze šablony">
      <p className="text-stone-700">
        Dávka se tiskne podle verze, se kterou se poprvé exportovala. Testovací tisk A4 vytiskněte na obyčejné tiskárně v
        měřítku 100 % a zkontrolujte rozměr a načtení QR kódu.
      </p>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-stone-300 text-stone-700">
            <tr>
              <th className="py-2 pr-3">Verze</th>
              <th className="py-2 pr-3">Vytvořeno</th>
              <th className="py-2 pr-3">Rozměr</th>
              <th className="py-2 pr-3 text-right">Dávek</th>
              <th className="py-2 pr-3">Poznámka</th>
              <th className="py-2">Soubory</th>
            </tr>
          </thead>
          <tbody>
            {versions.map((version) => (
              <tr key={version.id} className="border-b border-stone-200 align-top">
                <td className="py-2 pr-3 font-semibold">{version.version}</td>
                <td className="py-2 pr-3 whitespace-nowrap">{formatDateTime(version.createdAt)}</td>
                <td className="py-2 pr-3 whitespace-nowrap">
                  {formatNumber(version.layout.widthMm)} × {formatNumber(version.layout.heightMm)} mm
                </td>
                <td className="py-2 pr-3 text-right">{formatNumber(version.batchCount)}</td>
                <td className="py-2 pr-3">{version.note ?? '—'}</td>
                <td className="py-2">
                  <div className="flex flex-wrap gap-x-4">
                    <a href={adminApi.testPrintUrl(version.id)} download className={linkButton}>
                      Testovací tisk A4
                    </a>
                    <a href={adminApi.cardTemplateFileUrl(version.id, 'FRONT')} download className={linkButton}>
                      Přední grafika
                    </a>
                    <a href={adminApi.cardTemplateFileUrl(version.id, 'BACK')} download className={linkButton}>
                      Zadní grafika
                    </a>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Section>
  )
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset className="flex flex-wrap items-end gap-3 border-t border-stone-200 pt-3">
      <legend className="pr-2 font-bold">{title}</legend>
      {children}
    </fieldset>
  )
}

interface NumberFieldProps {
  label: string
  value: number
  onChange: (value: number) => void
  min?: number
  max?: number
  step?: number
}

/** A number that may be typed freely (also empty for a moment); only valid numbers reach the layout. */
function NumberField({ label, value, onChange, min = 0, max = 300, step = 0.5 }: NumberFieldProps) {
  const [text, setText] = useState(String(value))
  const [shown, setShown] = useState(value)
  if (value !== shown) {
    // Changed from outside (another format): show the new value
    setShown(value)
    setText(String(value))
  }
  return (
    <label className={fieldLabel}>
      {label}
      <input
        type="number"
        inputMode="decimal"
        min={min}
        max={max}
        step={step}
        value={text}
        onChange={(event) => {
          setText(event.target.value)
          const number = Number(event.target.value.replace(',', '.'))
          if (event.target.value.trim() !== '' && Number.isFinite(number) && number >= min && number <= max) {
            setShown(number)
            onChange(number)
          }
        }}
        className={`${field} font-normal`}
      />
    </label>
  )
}

function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)
  const key = JSON.stringify(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(JSON.parse(key) as T), delayMs)
    return () => clearTimeout(timer)
  }, [key, delayMs])
  return debounced
}

function formatSize(bytes: number): string {
  return bytes < 1024 * 1024 ? `${formatNumber(Math.round(bytes / 1024))} kB` : `${formatNumber(Math.round(bytes / 1024 / 102.4) / 10)} MB`
}
