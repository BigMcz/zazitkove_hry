const STORAGE_KEY = 'zazitky.sessionToken'

/**
 * Session token in localStorage. Storage may be unavailable (private mode, blocked site data),
 * then the game still works until the page is reloaded.
 */
export const tokenStorage = {
  get(): string | null {
    try {
      return window.localStorage.getItem(STORAGE_KEY)
    } catch {
      return null
    }
  },

  set(token: string): void {
    try {
      window.localStorage.setItem(STORAGE_KEY, token)
    } catch {
      // see above
    }
  },

  clear(): void {
    try {
      window.localStorage.removeItem(STORAGE_KEY)
    } catch {
      // see above
    }
  },
}
