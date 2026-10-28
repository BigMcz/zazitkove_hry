import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { adminApi, type CreateBatchRequest } from './adminApi.ts'
import Section from './Section.tsx'
import { adminKeys, fieldLabel, useLoggedOutOn401 } from './shared.ts'

export default function NewBatchForm() {
  const queryClient = useQueryClient()
  const games = useQuery({ queryKey: adminKeys.games, queryFn: adminApi.games })
  const [gameId, setGameId] = useState('')
  const [quantity, setQuantity] = useState('100')
  const [validityHours, setValidityHours] = useState('5')
  const [note, setNote] = useState('')
  const [unitPrice, setUnitPrice] = useState('')
  const createBatch = useMutation({
    mutationFn: (request: CreateBatchRequest) => adminApi.createBatch(request),
    onSuccess: () => {
      setNote('')
      return queryClient.invalidateQueries({ queryKey: adminKeys.batches('admin') })
    },
  })
  useLoggedOutOn401(games.error ?? createBatch.error)

  const selectedGame = gameId || games.data?.[0]?.id || ''

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createBatch.mutate({
      gameId: selectedGame,
      quantity: Number(quantity),
      validityHours: Number(validityHours),
      note: note.trim() || null,
      unitPriceCzk: unitPrice.trim() === '' ? null : Number(unitPrice),
    })
  }

  return (
    <Section title="Nová dávka kódů">
      <p className="text-stone-700">
        Vygenerované kódy jsou nové (nepoužité). Za prodaný se kód považuje až ve chvíli, kdy ho hráč zadá.
      </p>
      <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2">
        <label className={`${fieldLabel} sm:col-span-2`}>
          Hra
          <select value={selectedGame} onChange={(event) => setGameId(event.target.value)} className={input}>
            {games.data?.map((game) => (
              <option key={game.id} value={game.id}>
                {game.title} — {game.partnerName}
                {game.status !== 'PUBLISHED' ? ` (${game.status})` : ''}
              </option>
            ))}
          </select>
        </label>
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
        <label className={fieldLabel}>
          Cena za kód (Kč, nepovinné)
          <input
            type="number"
            min={0}
            value={unitPrice}
            onChange={(event) => setUnitPrice(event.target.value)}
            className={input}
          />
        </label>
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
            Dávka {createBatch.data.quantity} kódů je vytvořená. CSV pro tisk stáhneš v seznamu dávek.
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
