import { describe, expect, it } from 'vitest'
import { formatDuration } from './formatDuration.ts'

describe('formatDuration', () => {
  it.each([
    [0, '0 s'],
    [45, '45 s'],
    [60, '1 min'],
    [185, '3 min 05 s'],
    [2710, '45 min 10 s'],
    [3600, '1 h 00 min'],
    [3725, '1 h 02 min'],
    [-5, '0 s'],
  ])('%i s → %s', (seconds, expected) => {
    expect(formatDuration(seconds)).toBe(expected)
  })
})
