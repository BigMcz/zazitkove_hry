import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, partnerApi, type GamePrices as GamePricesData, type SetPriceRequest } from './adminApi.ts'
import { firstOfNextMonth, formatDate, formatPrice, todayInPrague } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, invalidateAdminData, linkButton, useLoggedOutOn401 } from './shared.ts'

const cell = 'min-h-11 w-full rounded-lg border-2 border-stone-400 bg-white px-2'

/**
 * Selling price of a card of each game over time: valid from a day until the next change. The operator sets it,
 * the castle sees it (what to sell the cards for at the ticket office).
 */
export default function GamePrices({ partnerId }: { partnerId?: string }) {
  const editable = partnerId !== undefined
  const prices = useQuery({
    queryKey: adminKeys.gamePrices(partnerId),
    queryFn: () => (partnerId ? adminApi.gamePrices(partnerId) : partnerApi.gamePrices()),
  })
  useLoggedOutOn401(prices.error)

  return (
    <Section title={editable ? 'Prodejní ceny her' : 'Ceny kartiček'}>
      <p className="text-stone-700">
        {editable
          ? 'Cena kartičky platí od zadaného dne do další změny. Každý aktivovaný kód se do tržby počítá cenou platnou v den aktivace (český čas). Jazykové verze hry mají stejnou cenu, kartičky jsou stejné.'
          : 'Za kolik se kartičky prodávají. Cenu nastavuje provozovatel, změnu uvidíte předem.'}
      </p>
      {prices.isPending && <p role="status">Načítám…</p>}
      {prices.isError && <ErrorBanner message={prices.error.message} onRetry={() => void prices.refetch()} />}
      {prices.data?.length === 0 && <p>Hrad zatím nemá žádnou hru.</p>}
      {prices.data?.map((game) =>
        editable ? <EditableGame key={game.gameId} game={game} /> : <ReadOnlyGame key={game.gameId} game={game} />,
      )}
    </Section>
  )
}

function ReadOnlyGame({ game }: { game: GamePricesData }) {
  const today = todayInPrague()
  const current = game.prices.filter((price) => price.validFrom <= today).at(-1)
  const planned = game.prices.filter((price) => price.validFrom > today)
  return (
    <p className="text-stone-800">
      <strong>{game.gameTitle}:</strong>{' '}
      {current ? (
        <>
          <strong>{formatPrice(current.priceCzk)}</strong> (od {formatDate(current.validFrom)})
        </>
      ) : (
        'cena zatím není nastavená'
      )}
      {planned.map((price) => (
        <span key={price.id}>
          {' '}
          · od {formatDate(price.validFrom)}: <strong>{formatPrice(price.priceCzk)}</strong>
        </span>
      ))}
    </p>
  )
}

function EditableGame({ game }: { game: GamePricesData }) {
  const queryClient = useQueryClient()
  const today = todayInPrague()
  const [validFrom, setValidFrom] = useState(() => firstOfNextMonth(today))
  const [priceCzk, setPriceCzk] = useState('')
  // The price changes the money in the report and the key figures, too
  const refresh = () => invalidateAdminData(queryClient)
  const setPrice = useMutation({
    mutationFn: (request: SetPriceRequest) => adminApi.setGamePrice(game.gameId, request),
    onSuccess: () => {
      setPriceCzk('')
      return refresh()
    },
  })
  const deletePrice = useMutation({
    mutationFn: (priceId: string) => adminApi.deleteGamePrice(game.gameId, priceId),
    onSuccess: refresh,
  })
  useLoggedOutOn401(setPrice.error ?? deletePrice.error)

  const currentId = game.prices.filter((price) => price.validFrom <= today).at(-1)?.id
  const replacesExisting = game.prices.some((price) => price.validFrom === validFrom)
  const error = setPrice.error ?? deletePrice.error

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setPrice.mutate({ validFrom, priceCzk: Number(priceCzk) })
  }

  return (
    <article aria-label={game.gameTitle} className="flex flex-col gap-3 border-t border-stone-200 pt-4">
      <h3 className="text-lg font-bold">
        {game.gameTitle}
        <span className="font-normal text-stone-600"> · dnes {formatPrice(game.currentPriceCzk)}</span>
      </h3>
      {game.prices.length === 0 ? (
        <p className="font-semibold text-amber-900">
          Zatím není nastavená žádná cena. Aktivované kódy se do tržby nezapočítají.
        </p>
      ) : (
        <table className="w-auto text-left text-sm">
          <caption className="sr-only">Ceny hry {game.gameTitle}</caption>
          <thead className="text-stone-700">
            <tr>
              <th className="py-1 pr-6">Platí od</th>
              <th className="py-1 pr-6 text-right">Cena</th>
              <th className="py-1" />
            </tr>
          </thead>
          <tbody>
            {game.prices.map((price) => (
              <tr key={price.id}>
                <td className="py-1 pr-6 whitespace-nowrap">
                  {formatDate(price.validFrom)}
                  {price.id === currentId && <span className="ml-2 font-semibold text-emerald-800">platí dnes</span>}
                  {price.validFrom > today && <span className="ml-2 text-stone-600">naplánováno</span>}
                </td>
                <td className="py-1 pr-6 text-right whitespace-nowrap">{formatPrice(price.priceCzk)}</td>
                <td className="py-1">
                  <button
                    type="button"
                    disabled={deletePrice.isPending}
                    onClick={() => deletePrice.mutate(price.id)}
                    aria-label={`Smazat cenu od ${formatDate(price.validFrom)}`}
                    className={linkButton}
                  >
                    Smazat
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 font-semibold">
          Platí od
          <input
            type="date"
            required
            value={validFrom}
            onChange={(event) => setValidFrom(event.target.value)}
            className={cell}
          />
        </label>
        <label className="flex flex-col gap-1 font-semibold">
          Cena (Kč)
          <input
            type="number"
            required
            min={0}
            max={100000}
            step={1}
            value={priceCzk}
            onChange={(event) => setPriceCzk(event.target.value)}
            className={`${cell} max-w-28`}
          />
        </label>
        <button type="submit" disabled={setPrice.isPending} className={`${linkButton} pb-2`}>
          {replacesExisting ? 'Nahradit cenu' : 'Přidat cenu'}
        </button>
      </form>
      {validFrom && validFrom <= today && (
        <p role="note" className="text-amber-900">
          Datum není v budoucnosti. Změní tržbu kódů aktivovaných od {formatDate(validFrom)}, tedy i měsíců, které už
          mohly být vyfakturované.
        </p>
      )}
      {error && <ErrorBanner message={error.message} />}
    </article>
  )
}
