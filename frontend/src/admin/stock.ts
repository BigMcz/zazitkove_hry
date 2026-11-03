import type { GameStock, PartnerStats } from './adminApi.ts'
import { formatDays } from './format.ts'

/** Below this many days of cards a castle is listed as running out (printing and handing over takes time). */
export const LOW_STOCK_DAYS = 21

/** How long the cards last at the pace of the last 28 days. */
export function stockLasts(row: PartnerStats | GameStock): string {
  if (row.stockDays !== null) {
    return `asi ${formatDays(row.stockDays)}`
  }
  return row.cardsInStock > 0 ? 'bez prodeje' : '—'
}

/** Cards of the game for less than LOW_STOCK_DAYS at the current pace, or none left after earlier deliveries. */
export function isRunningOut(game: GameStock): boolean {
  return (game.stockDays !== null && game.stockDays < LOW_STOCK_DAYS) || (game.cardsDelivered > 0 && game.cardsInStock === 0)
}
