import { describe, expect, it } from 'vitest'
import { firstOfNextMonth, formatDate, formatMonth, formatPercent } from './format.ts'

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
})
