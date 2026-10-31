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

/** Share of a whole in whole percent; "—" for an empty whole. */
export function formatShare(part: number, whole: number): string {
  return whole === 0 ? '—' : `${number.format(Math.round((part / whole) * 100))} %`
}

/** 3125 → "52 min", 4500 → "1 h 15 min" */
export function formatDuration(seconds: number | null): string {
  if (seconds === null) {
    return '—'
  }
  const minutes = Math.round(seconds / 60)
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')} min`
}

/** 1 den, 2 dny, 5 dní */
export function formatDays(days: number): string {
  const unit = days === 1 ? 'den' : days >= 2 && days <= 4 ? 'dny' : 'dní'
  return `${number.format(days)} ${unit}`
}

export function formatMoney(value: number | null): string {
  return value === null ? '—' : money.format(value)
}
