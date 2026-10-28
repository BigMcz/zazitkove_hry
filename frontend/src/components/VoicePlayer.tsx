import { useEffect, useRef, useState } from 'react'
import { voicePreference } from './audioPreference.ts'

interface VoicePlayerProps {
  src: string
  /** Start on its own when the page opens (unless the player muted the voice). */
  autoPlay?: boolean
}

/**
 * Spoken version of a text (the text itself stays on screen as subtitles). Browsers — iPhones
 * above all — often block sound that was not started by a tap; then the player simply taps
 * „Poslechnout“.
 */
export default function VoicePlayer({ src, autoPlay = true }: VoicePlayerProps) {
  const audio = useRef<HTMLAudioElement>(null)
  const [playing, setPlaying] = useState(false)
  const [muted, setMuted] = useState(() => voicePreference.isMuted())

  useEffect(() => {
    const element = audio.current
    if (!element || !autoPlay || voicePreference.isMuted()) {
      return
    }
    // Autoplay blocked — the play button stays available
    tryPlay(element)
    return () => element.pause()
  }, [src, autoPlay])

  function togglePlay() {
    const element = audio.current
    if (!element) {
      return
    }
    if (element.paused) {
      if (muted) {
        setMuted(false)
        voicePreference.setMuted(false)
      }
      tryPlay(element)
    } else {
      element.pause()
    }
  }

  function toggleMuted() {
    const next = !muted
    setMuted(next)
    voicePreference.setMuted(next)
    if (next) {
      audio.current?.pause()
    }
  }

  return (
    <div className="flex items-center gap-2">
      <audio
        ref={audio}
        src={src}
        preload="auto"
        onPlay={() => setPlaying(true)}
        onPause={() => setPlaying(false)}
        onEnded={() => setPlaying(false)}
      />
      <button
        type="button"
        onClick={togglePlay}
        className="inline-flex min-h-11 items-center gap-2 rounded-full bg-stone-900 px-4 font-semibold text-white hover:bg-stone-700"
      >
        {playing ? <PauseIcon /> : <PlayIcon />}
        {playing ? 'Pozastavit' : 'Poslechnout'}
      </button>
      <button
        type="button"
        onClick={toggleMuted}
        aria-pressed={muted}
        className="inline-flex min-h-11 items-center rounded-full px-3 text-sm font-semibold text-stone-700 underline underline-offset-4 hover:text-stone-950"
      >
        {muted ? 'Zapnout hlas' : 'Ztlumit'}
      </button>
    </div>
  )
}

/** play() returns a promise in current browsers, but not everywhere (older engines, test DOM). */
function tryPlay(element: HTMLAudioElement) {
  const result = element.play() as Promise<void> | undefined
  result?.catch(() => undefined)
}

function PlayIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
      <path d="M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z" />
    </svg>
  )
}

function PauseIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">
      <rect x="6" y="5" width="4" height="14" rx="1" />
      <rect x="14" y="5" width="4" height="14" rx="1" />
    </svg>
  )
}
