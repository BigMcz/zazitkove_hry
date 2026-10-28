import { afterEach, describe, expect, it, vi } from 'vitest'
import { tokenStorage } from './tokenStorage.ts'

describe('tokenStorage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
  })

  it('stores, reads and clears the token', () => {
    tokenStorage.set('abc')
    expect(tokenStorage.get()).toBe('abc')

    tokenStorage.clear()
    expect(tokenStorage.get()).toBeNull()
  })

  it('does not throw when storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError')
    })

    expect(() => tokenStorage.set('abc')).not.toThrow()
    expect(tokenStorage.get()).toBeNull()
  })
})
