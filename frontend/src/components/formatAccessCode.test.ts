import { describe, expect, it } from 'vitest'
import { formatAccessCode } from './formatAccessCode.ts'

describe('formatAccessCode', () => {
  it('adds the dash after the fourth typed character', () => {
    expect(formatAccessCode('abc', 'ab')).toBe('ABC')
    expect(formatAccessCode('abcd', 'abc')).toBe('ABCD-')
    expect(formatAccessCode('ABCD-2', 'ABCD-')).toBe('ABCD-2')
  })

  it('lets the dash be deleted', () => {
    expect(formatAccessCode('ABCD', 'ABCD-')).toBe('ABCD')
    expect(formatAccessCode('ABCD-', 'ABCD-2')).toBe('ABCD-')
  })

  it('ignores a dash typed by the player', () => {
    expect(formatAccessCode('ABCD--', 'ABCD-')).toBe('ABCD-')
  })

  it('formats a pasted code and cuts it to eight characters', () => {
    expect(formatAccessCode('abcd 2345', '')).toBe('ABCD-2345')
    expect(formatAccessCode('demo-kata-xyz', '')).toBe('DEMO-KATA')
  })
})
