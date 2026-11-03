import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, buttonSecondary } from '../components/ui.ts'
import { adminApi, type CardSide, type PrintFile, type PrintStatus } from './adminApi.ts'
import CardPreviewImage from './CardPreviewImage.tsx'
import { saveFile } from './download.ts'
import { formatDateTime, formatNumber } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, fieldLabel, linkButton, useLoggedOutOn401 } from './shared.ts'

const kindLabels = { FRONT: 'Přední strana', BACK: 'Zadní strana', ZIP: 'Vše (ZIP)' } as const

/**
 * Printing a batch: the template version it is printed with, a preview with its first code and the downloads for
 * the printer (front once, back one page per code, a reprint of chosen cards).
 */
export default function BatchPrint() {
  const { batchId = '' } = useParams()
  const status = useQuery({ queryKey: adminKeys.batchPrint(batchId), queryFn: () => adminApi.batchPrint(batchId) })
  useLoggedOutOn401(status.error)

  if (status.isPending) {
    return <p role="status">Načítám…</p>
  }
  if (status.isError) {
    return <ErrorBanner message={status.error.message} onRetry={() => void status.refetch()} />
  }
  const batch = status.data
  const template = batch.template ?? batch.latestTemplate
  return (
    <>
      <header className="flex flex-col gap-1">
        <Link to={`/admin/partneri/${batch.partnerId}?sekce=kody`} className={`${linkButton} self-start`}>
          Zpět na kódy hradu
        </Link>
        <h2 className="text-2xl font-bold">
          Tisk dávky D{batch.batchNo}: {batch.gameTitle}
        </h2>
        <p className="text-sm text-stone-600">
          {[
            batch.partnerName,
            batch.multiSession ? `demo kód ${batch.sharedCode}` : `${formatNumber(batch.quantity)} kartiček`,
            batch.free && 'zdarma',
            batch.handedOverAt ? `předáno ${formatDateTime(batch.handedOverAt)}` : 've výrobě',
            batch.printedAt ? `poprvé tištěno ${formatDateTime(batch.printedAt)}` : 'zatím netištěno',
          ]
            .filter(Boolean)
            .join(' · ')}
        </p>
      </header>
      {batch.handedOverAt && (
        <p role="note" className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-amber-900">
          Dávka je předaná, kódy v PDF už platí. Stahujte jen pro dotisk a soubory nikomu dalšímu neposílejte.
        </p>
      )}
      <TemplateChoice batch={batch} />
      {template && (
        <>
          <Section title="Náhled s prvním kódem dávky">
            <div className="grid gap-6 lg:grid-cols-2">
              <BatchPreview batchId={batch.batchId} side="FRONT" templateId={template.id} />
              <BatchPreview batchId={batch.batchId} side="BACK" templateId={template.id} />
            </div>
          </Section>
          <Downloads batch={batch} />
        </>
      )}
      <Exports batch={batch} />
    </>
  )
}

/** Which version the batch is printed with; it can change only while the batch is in production. */
function TemplateChoice({ batch }: { batch: PrintStatus }) {
  const queryClient = useQueryClient()
  const versions = useQuery({
    queryKey: adminKeys.cardTemplates(batch.gameId),
    queryFn: () => adminApi.cardTemplates(batch.gameId),
  })
  const bind = useMutation({
    mutationFn: (templateId: string) => adminApi.bindBatchTemplate(batch.batchId, templateId),
    onSuccess: (status) => {
      queryClient.setQueryData(adminKeys.batchPrint(batch.batchId), status)
      return queryClient.invalidateQueries({ queryKey: ['admin', 'batches'] })
    },
  })
  useLoggedOutOn401(versions.error ?? bind.error)
  const editorUrl = `/admin/games/${batch.gameId}/karticka?davka=${batch.batchId}`

  if (!batch.latestTemplate) {
    return (
      <Section title="Šablona kartičky">
        <p className="font-semibold text-amber-900">Hra zatím nemá šablonu kartičky, bez ní tisk nejde.</p>
        <Link to={editorUrl} className={`${buttonPrimary} sm:w-auto`}>
          Vytvořit šablonu
        </Link>
      </Section>
    )
  }

  const current = batch.template ?? batch.latestTemplate
  const outdated = batch.template !== null && batch.template.version < batch.latestTemplate.version
  return (
    <Section title="Šablona kartičky">
      <p>
        {batch.template ? (
          <>
            Dávka se tiskne podle <strong>verze {batch.template.version}</strong>.
          </>
        ) : (
          <>
            Dávka zatím není tištěná, první stažení ji přiváže k <strong>verzi {batch.latestTemplate.version}</strong>{' '}
            (poslední).
          </>
        )}{' '}
        {current.note && <span className="text-stone-600">({current.note})</span>}
      </p>
      {outdated && (
        <p className="text-amber-900">
          Hra má novější verzi {batch.latestTemplate.version}.{' '}
          {batch.handedOverAt
            ? 'Dávka je předaná, dotisk zůstává podle verze, se kterou se tiskla.'
            : 'Dávka je ve výrobě, můžete ji přepnout.'}
        </p>
      )}
      <div className="flex flex-wrap items-end gap-4">
        {!batch.handedOverAt && versions.data && versions.data.length > 1 && (
          <label className={fieldLabel}>
            Tisknout podle verze
            <select
              value={current.id}
              disabled={bind.isPending}
              onChange={(event) => bind.mutate(event.target.value)}
              className="min-h-11 rounded-lg border-2 border-stone-400 bg-white px-2 font-normal"
            >
              {versions.data.map((version) => (
                <option key={version.id} value={version.id}>
                  verze {version.version}
                  {version.note ? `: ${version.note}` : ''}
                </option>
              ))}
            </select>
          </label>
        )}
        <Link to={editorUrl} className={linkButton}>
          Upravit šablonu (nová verze)
        </Link>
        <a href={adminApi.testPrintUrl(current.id)} download className={linkButton}>
          Testovací tisk A4
        </a>
      </div>
      {bind.isError && <ErrorBanner message={bind.error.message} />}
    </Section>
  )
}

function BatchPreview({ batchId, side, templateId }: { batchId: string; side: CardSide; templateId: string }) {
  const preview = useQuery({
    // templateId: a new version (or another one chosen) draws a new picture
    queryKey: ['admin', 'batch-preview', batchId, side, templateId],
    queryFn: () => adminApi.batchPreview(batchId, side),
    retry: false,
  })
  useLoggedOutOn401(preview.error)
  return (
    <CardPreviewImage
      label={side === 'FRONT' ? 'Přední strana' : 'Zadní strana'}
      preview={preview.data}
      loading={preview.isFetching}
      error={preview.error}
    />
  )
}

function Downloads({ batch }: { batch: PrintStatus }) {
  const queryClient = useQueryClient()
  const [cropMarks, setCropMarks] = useState(false)
  const [serials, setSerials] = useState('')
  // A demo code goes on as many cards as needed
  const [copies, setCopies] = useState('10')
  const cards = batch.multiSession ? Math.max(1, Math.min(1000, Number(copies) || 1)) : batch.quantity
  const download = useMutation({
    mutationFn: ({ file, reprint }: { file: PrintFile; reprint?: string }) =>
      adminApi.exportBatch(batch.batchId, file, cropMarks, reprint ?? null, batch.multiSession ? cards : null),
    onSuccess: async ({ blob, fileName }, { file }) => {
      saveFile(blob, fileName ?? file)
      // The first export ties the version and marks the batch as printed; the audit grows
      await queryClient.invalidateQueries({ queryKey: adminKeys.batchPrint(batch.batchId) })
      await queryClient.invalidateQueries({ queryKey: ['admin', 'batches'] })
    },
  })
  useLoggedOutOn401(download.error)

  function handleReprint(event: FormEvent) {
    event.preventDefault()
    download.mutate({ file: 'back.pdf', reprint: serials })
  }

  return (
    <Section title="Stažení pro tiskárnu">
      <p className="text-stone-700">
        Přední strana je pro všechny kartičky stejná (1 strana), zadní má co kód, to stranu. ZIP obsahuje obě PDF a
        seznam kódů v CSV. Generování velké dávky trvá několik sekund.
      </p>
      {batch.multiSession && (
        <label className={fieldLabel}>
          Počet kartiček s demo kódem {batch.sharedCode}
          <input
            type="number"
            min={1}
            max={1000}
            value={copies}
            onChange={(event) => setCopies(event.target.value)}
            className="min-h-11 w-32 rounded-lg border-2 border-stone-400 bg-white px-2 font-normal"
          />
        </label>
      )}
      <label className="flex min-h-11 items-center gap-2 font-semibold">
        <input type="checkbox" checked={cropMarks} onChange={(event) => setCropMarks(event.target.checked)} />
        Ořezové značky
      </label>
      <div className="grid gap-3 sm:grid-cols-3">
        <button
          type="button"
          disabled={download.isPending}
          onClick={() => download.mutate({ file: 'front.pdf' })}
          className={buttonSecondary}
        >
          Přední strana (PDF)
        </button>
        <button
          type="button"
          disabled={download.isPending}
          onClick={() => download.mutate({ file: 'back.pdf' })}
          className={buttonSecondary}
        >
          Zadní strana (PDF, {formatNumber(cards)} stran)
        </button>
        <button
          type="button"
          disabled={download.isPending}
          onClick={() => download.mutate({ file: 'all.zip' })}
          className={buttonPrimary}
        >
          Vše (ZIP)
        </button>
      </div>
      {!batch.multiSession && (
        <form onSubmit={handleReprint} className="flex flex-wrap items-end gap-3 border-t border-stone-200 pt-3">
          <label className={fieldLabel}>
            Dotisk kartiček s pořadovými čísly
            <input
              value={serials}
              onChange={(event) => setSerials(event.target.value)}
              placeholder="42, 57, 100-110"
              className="min-h-11 w-64 rounded-lg border-2 border-stone-400 bg-white px-2 font-normal"
            />
          </label>
          <button type="submit" disabled={download.isPending || serials.trim() === ''} className={linkButton}>
            Stáhnout dotisk (PDF)
          </button>
        </form>
      )}
      {download.isPending && <p role="status">Generuji…</p>}
      {download.isError && <ErrorBanner message={download.error.message} />}
    </Section>
  )
}

function Exports({ batch }: { batch: PrintStatus }) {
  if (batch.exports.length === 0) {
    return null
  }
  return (
    <Section title="Historie stažení">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-stone-300 text-stone-700">
            <tr>
              <th className="py-2 pr-3">Kdy</th>
              <th className="py-2 pr-3">Co</th>
              <th className="py-2 pr-3 text-right">Kódů</th>
              <th className="py-2 pr-3">Verze</th>
              <th className="py-2">Kdo</th>
            </tr>
          </thead>
          <tbody>
            {batch.exports.map((item) => (
              <tr key={`${item.createdAt}-${item.kind}`} className="border-b border-stone-200">
                <td className="py-2 pr-3 whitespace-nowrap">{formatDateTime(item.createdAt)}</td>
                <td className="py-2 pr-3">
                  {kindLabels[item.kind]}
                  {item.serials && ` (dotisk ${item.serials})`}
                  {item.cropMarks && ', s ořezovými značkami'}
                </td>
                <td className="py-2 pr-3 text-right">{item.kind === 'FRONT' ? '—' : formatNumber(item.codesCount)}</td>
                <td className="py-2 pr-3">{item.templateVersion}</td>
                <td className="py-2">{item.username}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Section>
  )
}
