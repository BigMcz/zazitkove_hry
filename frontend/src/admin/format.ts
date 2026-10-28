const ZONE = 'Europe/Prague'

const dateTime = new Intl.DateTimeFormat('cs-CZ', { dateStyle: 'short', timeStyle: 'short', timeZone: ZONE })
const monthName = new Intl.DateTimeFormat('cs-CZ', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const number = new Intl.NumberFormat('cs-CZ')
const money = new Intl.NumberFormat('cs-CZ', { style: 'currency', currency: 'CZK', maximumFractionDigits: 2 })

/** Today in the castles' time zone as YYYY-MM-DD. */
export function todayInPrague(): string {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: ZONE }).format(new Date())
}

/** First day of the next month after the given YYYY-MM-DD — the usual start of a new share. */
export function firstOfNextMonth(isoDate: string): string {
  const [year, month] = isoDate.split('-').map(Number)
  const next = month === 12 ? { year: year + 1, month: 1 } : { year, month: month + 1 }
  return `${next.year}-${String(next.month).padStart(2, '0')}-01`
}

/** "2026-12-01" → "1. 12. 2026" */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return `${day}. ${month}. ${year}`
}

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso))
}

/** "2026-10" → "říjen 2026" */
export function formatMonth(month: string): string {
  const [year, monthNo] = month.split('-').map(Number)
  return monthName.format(new Date(Date.UTC(year, monthNo - 1, 1)))
}

export function formatNumber(value: number): string {
  return number.format(value)
}

export function formatPercent(value: number | null): string {
  return value === null ? '—' : `${number.format(value)} %`
}

export function formatMoney(value: number | null): string {
  return value === null ? '—' : money.format(value)
}
