import { describe, expect, it } from 'vitest'
import {
  firstOfNextMonth,
  formatDate,
  formatDays,
  formatDuration,
  formatMonth,
  formatPercent,
  formatShare,
} from './format.ts'

describe('admin formatting', () => {
  it.each([
    ['2026-10-28', '2026-11-01'],
    ['2026-12-31', '2027-01-01'],
    ['2026-01-01', '2026-02-01'],
  ])('first of the month after %s is %s', (today, expected) => {
    expect(firstOfNextMonth(today)).toBe(expected)
  })

  it('formats dates, months and percentages in Czech', () => {
    expect(formatDate('2026-12-01')).toBe('1. 12. 2026')
    expect(formatMonth('2026-10')).toBe('říjen 2026')
    expect(formatPercent(25)).toBe('25 %')
    expect(formatPercent(12.5)).toBe('12,5 %')
    expect(formatPercent(null)).toBe('—')
  })

  it('formats durations, days and shares for the statistics', () => {
    expect(formatDuration(3125)).toBe('52 min')
    expect(formatDuration(4500)).toBe('1 h 15 min')
    expect(formatDuration(3600)).toBe('1 h 00 min')
    expect(formatDuration(null)).toBe('—')
    expect([1, 3, 5, 0].map(formatDays)).toEqual(['1 den', '3 dny', '5 dní', '0 dní'])
    expect(formatShare(2, 3)).toBe('67 %')
    expect(formatShare(0, 0)).toBe('—')
  })
})
