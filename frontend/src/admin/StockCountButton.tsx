import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { buttonPrimary, input } from '../components/ui.ts'
import { partnerApi, type Summary } from './adminApi.ts'
import Dialog from './Dialog.tsx'
import { formatNumber } from './format.ts'
import { invalidateAdminData, linkButton, useLoggedOutOn401 } from './shared.ts'

/**
 * The castle enters how many cards of each game it really has: a card sold but never activated still
 * counts as in stock for us. Only lowering is possible, the money is not affected.
 */
export default function StockCountButton({ year, summary }: { year: number; summary: Summary }) {
  const [open, setOpen] = useState(false)

  return (
    <>
      <button type="button" onClick={() => setOpen(true)} className={`${linkButton} self-start text-sm`}>
        Upravit podle skutečnosti
      </button>
      {open && (
        <Dialog title="Kartičky na skladě" onClose={() => setOpen(false)}>
          <StockCountForm year={year} summary={summary} onDone={() => setOpen(false)} />
        </Dialog>
      )}
    </>
  )
}

function StockCountForm({ year, summary, onDone }: { year: number; summary: Summary; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [cards, setCards] = useState<Record<string, string>>(() =>
    Object.fromEntries(summary.games.map((game) => [game.gameId, String(game.cardsInStock)])),
  )
  const save = useMutation({
    mutationFn: () =>
      partnerApi.stockCount(
        year,
        summary.games.map((game) => ({ gameId: game.gameId, cardsInStock: Number(cards[game.gameId]) })),
      ),
    onSuccess: async () => {
      await invalidateAdminData(queryClient)
      onDone()
    },
  })
  useLoggedOutOn401(save.error)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    save.mutate()
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3">
      <p className="text-stone-700">
        Kartičku, kterou si zákazník koupil, ale hru nespustil, počítáme pořád jako skladovou. Spočítejte kartičky,
        které máte opravdu, a zadejte jejich počet. Dál se bude počítat od něj.
      </p>
      <p className="text-sm text-stone-600">
        U každé hry nejvýš tolik, kolik převzatých kartiček zatím nikdo neaktivoval. Vyúčtování se nemění, účtují
        se jen aktivované kartičky.
      </p>
      {summary.games.map((game) => (
        <label key={game.gameId} className="flex flex-col gap-1 font-semibold">
          {game.gameTitle}
          <span className="text-sm font-normal text-stone-600">
            nejvýš {formatNumber(game.cardsInStockComputed)}
          </span>
          <input
            type="number"
            inputMode="numeric"
            required
            min={0}
            max={game.cardsInStockComputed}
            value={cards[game.gameId] ?? ''}
            onChange={(event) => setCards((current) => ({ ...current, [game.gameId]: event.target.value }))}
            className={input}
          />
        </label>
      ))}
      {save.isError && <ErrorBanner message={save.error.message} />}
      <button type="submit" disabled={save.isPending} className={buttonPrimary}>
        Uložit
      </button>
    </form>
  )
}
