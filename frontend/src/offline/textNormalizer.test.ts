import { describe, expect, it } from 'vitest'
import cases from '../../../test-fixtures/text-normalizer.json?raw'
import { normalize, normalizeNumber } from './textNormalizer.ts'

const { text, number } = JSON.parse(cases) as { text: [string, string][]; number: [string, string][] }

describe('textNormalizer (cases shared with the backend)', () => {
  it.each(text)('normalize(%j) = %j', (input, expected) => {
    expect(normalize(input)).toBe(expected)
  })

  it.each(number)('normalizeNumber(%j) = %j', (input, expected) => {
    expect(normalizeNumber(input)).toBe(expected)
  })
})
