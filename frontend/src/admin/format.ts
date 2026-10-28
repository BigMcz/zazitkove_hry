const ZONE = 'Europe/Prague'

const dateTime = new Intl.DateTimeFormat('cs-CZ', { dateStyle: 'short', timeStyle: 'short', timeZone: ZONE })
const monthName = new Intl.DateTimeFormat('cs-CZ', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const number = new Intl.NumberFormat('cs-CZ')
const money = new Intl.NumberFormat('cs-CZ', { style: 'currency', currency: 'CZK', maximumFractionDigits: 2 })

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
