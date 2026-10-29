import { describe, expect, it } from 'vitest'
import { praiseForHints } from './praiseForHints.ts'

describe('praiseForHints', () => {
  it('praises most a game without hints', () => {
    expect(praiseForHints(0)).toContain('bez jediné nápovědy')
    expect(praiseForHints(3)).toContain('jen výjimečně')
    expect(praiseForHints(4)).toContain('došli sami')
  })
})
