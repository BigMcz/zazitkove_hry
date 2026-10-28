import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import VoicePlayer from './VoicePlayer.tsx'

// jsdom has no media playback: simulate play/pause including the events the player listens to
let playMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  playMock = vi.fn(function (this: HTMLMediaElement) {
    Object.defineProperty(this, 'paused', { value: false, configurable: true })
    this.dispatchEvent(new Event('play'))
    return Promise.resolve()
  })
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(playMock as never)
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(function (this: HTMLMediaElement) {
    Object.defineProperty(this, 'paused', { value: true, configurable: true })
    this.dispatchEvent(new Event('pause'))
  })
})

afterEach(() => {
  vi.restoreAllMocks()
  window.localStorage.clear()
})

describe('VoicePlayer', () => {
  it('starts speaking on its own', async () => {
    render(<VoicePlayer src="/audio/intro.mp3" />)

    expect(playMock).toHaveBeenCalledOnce()
    expect(await screen.findByRole('button', { name: 'Pozastavit' })).toBeInTheDocument()
  })

  it('keeps the play button when the browser blocks autoplay', async () => {
    playMock.mockImplementationOnce(() => Promise.reject(new DOMException('blocked', 'NotAllowedError')))
    render(<VoicePlayer src="/audio/intro.mp3" />)
    await act(async () => {})

    await userEvent.click(screen.getByRole('button', { name: 'Poslechnout' }))

    expect(playMock).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('button', { name: 'Pozastavit' })).toBeInTheDocument()
  })

  it('pauses and plays again', async () => {
    render(<VoicePlayer src="/audio/intro.mp3" />)

    await userEvent.click(await screen.findByRole('button', { name: 'Pozastavit' }))
    expect(screen.getByRole('button', { name: 'Poslechnout' })).toBeInTheDocument()
  })

  it('remembers muting: next texts do not start on their own', async () => {
    const { unmount } = render(<VoicePlayer src="/audio/intro.mp3" />)
    await userEvent.click(screen.getByRole('button', { name: 'Ztlumit' }))
    expect(screen.getByRole('button', { name: 'Zapnout hlas' })).toHaveAttribute('aria-pressed', 'true')
    unmount()
    playMock.mockClear()

    render(<VoicePlayer src="/audio/reward.mp3" />)

    expect(playMock).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Poslechnout' })).toBeInTheDocument()
  })

  it('playing by hand turns the voice back on', async () => {
    window.localStorage.setItem('zazitky.voiceMuted', '1')
    render(<VoicePlayer src="/audio/intro.mp3" />)
    expect(playMock).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Poslechnout' }))

    expect(playMock).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Ztlumit' })).toBeInTheDocument()
  })
})
