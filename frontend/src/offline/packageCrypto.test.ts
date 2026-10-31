import { describe, expect, it } from 'vitest'
import fixture from '../../../test-fixtures/offline-crypto.json?raw'
import { answerKey, contentKey, open, solveStation } from './packageCrypto.ts'

const shared = JSON.parse(fixture) as {
  answer: string
  wrongAnswer: string
  salt: string
  iterations: number
  contentKey: string
  key: { iv: string; data: string }
  content: { iv: string; data: string }
  plaintext: string
}

describe('packageCrypto (case sealed by the backend)', () => {
  it('opens what the backend sealed with the right answer', async () => {
    const key = await answerKey(shared.answer, shared.salt, shared.iterations)
    const rawKey = new Uint8Array(await open(key, shared.key))
    expect(btoa(String.fromCharCode(...rawKey))).toBe(shared.contentKey)

    const content = await open(await contentKey(shared.contentKey), shared.content)
    expect(new TextDecoder().decode(content)).toBe(shared.plaintext)
  })

  it('solves a station only with an accepted answer', async () => {
    const station = { id: 's1', salt: shared.salt, keys: [shared.key], content: shared.content }

    const solved = await solveStation(station, shared.answer, shared.iterations)
    expect(solved?.rawKey).toBe(shared.contentKey)
    expect(solved?.unlocked.rewardText).toBe('Správně.')

    expect(await solveStation(station, shared.wrongAnswer, shared.iterations)).toBeNull()
    expect(await solveStation(station, '', shared.iterations)).toBeNull()
  })
})
