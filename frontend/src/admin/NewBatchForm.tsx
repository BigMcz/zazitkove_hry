import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { adminApi, type BatchKind, type CreateBatchRequest } from './adminApi.ts'
import Section from './Section.tsx'
import { adminKeys, fieldLabel, invalidateAdminData, useLoggedOutOn401 } from './shared.ts'

const kinds: { id: BatchKind; label: string; description: string }[] = [
  { id: 'SALE', label: 'Prodejní', description: 'Kartičky na prodej na pokladně. Každá má svůj jednorázový kód.' },
  {
    id: 'FREE',
    label: 'Zdarma',
    description: 'Jednorázové kódy jako u prodejních (dárky, soutěže, novináři), ale nepočítají se do tržby, fakturace ani skladu hradu.',
  },
  {
    id: 'DEMO',
    label: 'Demo kód',
    description: 'Jeden společný kód (např. DEMO-KATA) na libovolném počtu kartiček. Neexpiruje, každé zadání spustí novou hru, nepočítá se do tržby ani statistik. Kdo kód zná, hraje zdarma.',
  },
]

/** Generates a batch of codes for one partner's game; the batch starts "in production". */
export default function NewBatchForm({ partnerId }: { partnerId: string }) {
  const queryClient = useQueryClient()
  const allGames = useQuery({ queryKey: adminKeys.games, queryFn: adminApi.games })
  const games = allGames.data?.filter((game) => game.partnerId === partnerId)
  const [gameId, setGameId] = useState('')
  const [kind, setKind] = useState<BatchKind>('SALE')
  const [demoCode, setDemoCode] = useState('')
  const [quantity, setQuantity] = useState('100')
  const [validityHours, setValidityHours] = useState('5')
  const [note, setNote] = useState('')
  const createBatch = useMutation({
    mutationFn: (request: CreateBatchRequest) => adminApi.createBatch(request),
    onSuccess: () => {
      setNote('')
      setDemoCode('')
      return invalidateAdminData(queryClient)
    },
  })
  useLoggedOutOn401(allGames.error ?? createBatch.error)

  const selectedGame = gameId || games?.[0]?.id || ''

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const demo = kind === 'DEMO'
    createBatch.mutate({
      gameId: selectedGame,
      kind,
      quantity: demo ? null : Number(quantity),
      validityHours: demo ? null : Number(validityHours),
      note: note.trim() || null,
      code: demo ? demoCode.trim() || null : null,
    })
  }

  return (
    <Section title="Nová dávka kódů">
      <p className="text-stone-700">
        Nová dávka je <strong>ve výrobě</strong> — kódy začnou fungovat, až dávku po předání hradu označíte jako
        předanou. Za prodaný se kód považuje až ve chvíli, kdy ho hráč zadá.
      </p>
      {games?.length === 0 && (
        <p role="status" className="font-semibold text-amber-900">
          Hrad zatím nemá žádnou hru, pro kterou by šly kódy vygenerovat.
        </p>
      )}
      <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2">
        <label className={`${fieldLabel} sm:col-span-2`}>
          Hra
          <select value={selectedGame} onChange={(event) => setGameId(event.target.value)} className={input}>
            {games?.map((game) => (
              <option key={game.id} value={game.id}>
                {game.title}
                {game.status !== 'PUBLISHED' ? ` (${game.status})` : ''}
              </option>
            ))}
          </select>
        </label>
        <fieldset className="flex flex-col gap-2 sm:col-span-2">
          <legend className="font-semibold">Typ</legend>
          {kinds.map((option) => (
            <label key={option.id} className="flex items-start gap-2">
              <input
                type="radio"
                name="batch-kind"
                checked={kind === option.id}
                onChange={() => setKind(option.id)}
                className="mt-1.5"
              />
              <span>
                <strong>{option.label}</strong>
                <span className="block text-sm text-stone-600">{option.description}</span>
              </span>
            </label>
          ))}
        </fieldset>
        {kind === 'DEMO' ? (
          <label className={`${fieldLabel} sm:col-span-2`}>
            Vlastní kód (nepovinné, 8 písmen nebo číslic)
            <input
              maxLength={9}
              placeholder="DEMO-KATA, prázdné = vygenerovat"
              value={demoCode}
              onChange={(event) => setDemoCode(event.target.value.toUpperCase())}
              className={input}
            />
          </label>
        ) : (
          <>
            <label className={fieldLabel}>
              Počet kódů
              <input
                type="number"
                min={1}
                max={5000}
                required
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
                className={input}
              />
            </label>
            <label className={fieldLabel}>
              Platnost od aktivace (hodiny)
              <input
                type="number"
                min={1}
                max={24}
                required
                value={validityHours}
                onChange={(event) => setValidityHours(event.target.value)}
                className={input}
              />
            </label>
          </>
        )}
        <label className={fieldLabel}>
          Poznámka (nepovinné)
          <input
            maxLength={300}
            placeholder="Sezóna 2027 — tisk 1"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            className={input}
          />
        </label>
        {createBatch.isError && (
          <div className="sm:col-span-2">
            <ErrorBanner message={createBatch.error.message} />
          </div>
        )}
        {createBatch.isSuccess && (
          <p role="status" className="font-semibold text-emerald-800 sm:col-span-2">
            {createBatch.data.multiSession
              ? `Demo dávka D${createBatch.data.batchNo} je vytvořená (ve výrobě). Kód a tisk najdete v seznamu dávek pod „Tisk“.`
              : `Dávka D${createBatch.data.batchNo} s ${createBatch.data.quantity} kódy je vytvořená (ve výrobě). Kartičky vytisknete v seznamu dávek pod „Tisk“.`}
          </p>
        )}
        <button
          type="submit"
          disabled={createBatch.isPending || !selectedGame}
          className={`${buttonPrimary} sm:col-span-2`}
        >
          {createBatch.isPending ? 'Generuji…' : 'Vygenerovat kódy'}
        </button>
      </form>
    </Section>
  )
}
