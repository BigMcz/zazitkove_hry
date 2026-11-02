import { voicePreference } from './audioPreference.ts'

/**
 * Plays a short recording without a player (e.g. Kryštof after a wrong answer), unless the player muted the voice.
 * Other recordings on the page stop, so that the voices do not talk over each other.
 */
export function playOnce(src: string) {
  if (voicePreference.isMuted()) {
    return
  }
  document.querySelectorAll('audio').forEach((element) => element.pause())
  // play() returns a promise in current browsers, but not everywhere; a blocked sound just stays silent
  const result = new Audio(src).play() as Promise<void> | undefined
  result?.catch(() => undefined)
}
