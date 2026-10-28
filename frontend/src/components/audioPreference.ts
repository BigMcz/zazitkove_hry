const STORAGE_KEY = 'zazitky.voiceMuted'

/**
 * Whether the player turned the spoken texts off (e.g. on a crowded courtyard). Kept in
 * localStorage; when storage is unavailable the voice is simply on.
 */
export const voicePreference = {
  isMuted(): boolean {
    try {
      return window.localStorage.getItem(STORAGE_KEY) === '1'
    } catch {
      return false
    }
  },

  setMuted(muted: boolean): void {
    try {
      if (muted) {
        window.localStorage.setItem(STORAGE_KEY, '1')
      } else {
        window.localStorage.removeItem(STORAGE_KEY)
      }
    } catch {
      // see above
    }
  },
}
