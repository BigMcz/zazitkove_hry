import { describe, expect, it } from 'vitest'
import {
  addDays,
  addMonths,
  firstOfNextMonth,
  formatDate,
  formatDays,
  formatDuration,
  formatMonth,
  formatPercent,
  formatPrice,
  formatShare,
} from './format.ts'

describe('admin formatting', () => {
  it.each([
    ['2026-11', -1, '2026-10'],
    ['2026-01', -1, '2025-12'],
    ['2026-12', 1, '2027-01'],
  ])('%s moved by %i months is %s', (month, months, expected) => {
    expect(addMonths(month, months)).toBe(expected)
  })

  it('formats the price of a card in whole crowns', () => {
    expect(formatPrice(249).replace(/\s/g, ' ')).toBe('249 Kč')
    expect(formatPrice(1250).replace(/\s/g, ' ')).toBe('1 250 Kč')
    expect(formatPrice(null)).toBe('—')
  })

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
    expect(addDays('2026-11-02', 20)).toBe('2026-11-22')
    expect(addDays('2026-12-20', 15)).toBe('2027-01-04')
  })
})
