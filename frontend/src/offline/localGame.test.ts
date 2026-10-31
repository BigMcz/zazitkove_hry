import { describe, expect, it } from 'vitest'
import { sealedPackage } from '../test/sealedPackage.ts'
import { NOT_A_NUMBER_MESSAGE, WRONG_ANSWER_MESSAGE, localAnswer, localHint, localState } from './localGame.ts'
import { type LocalProgress, emptyProgress } from './storage.ts'
import type { GamePackage } from './types.ts'

const DURING_GAME = new Date('2026-10-31T10:30:00Z')

function game(): Promise<GamePackage> {
  return sealedPackage([
    { id: 's1', title: 'Brána', taskType: 'TEXT', answers: ['ohyb reky'], hints: [60, 120], letter: 'K' },
    { id: 's2', title: 'Věž', taskType: 'NUMBER', answers: ['56'], letter: 'A' },
    { id: 's3', title: 'Sklep', taskType: 'TEXT', answers: ['kat', 'mistr kat'] },
  ])
}

/** Plays a correct answer and stores its change, like playClient does. */
async function solve(pack: GamePackage, progress: LocalProgress, stationId: string, answer: string, at = DURING_GAME) {
  const { solved, finished } = await localAnswer(pack, progress, stationId, answer, at)
  expect(solved).not.toBeNull()
  return {
    ...progress,
    solved: [...progress.solved, solved!],
    finishedAt: finished ? at.toISOString() : progress.finishedAt,
  }
}

describe('localGame', () => {
  it('starts on the first station', async () => {
    const pack = await game()
    const state = await localState(pack, emptyProgress(), DURING_GAME)

    expect(state.status).toBe('IN_PROGRESS')
    expect(state.currentStation).toMatchObject({ id: 's1', title: 'Brána', hintsAvailable: 2, nextHintPenaltySeconds: 60 })
    expect(state.progress).toEqual({ solved: 0, total: 3 })
    expect(state.puzzle).toEqual({ slots: [null, null], latestSlot: null })
  })

  it('evaluates answers like the server', async () => {
    const pack = await game()

    expect((await localAnswer(pack, emptyProgress(), 's1', 'Tvar věže', DURING_GAME)).result).toEqual({
      correct: false,
      message: WRONG_ANSWER_MESSAGE,
    })
    const correct = await localAnswer(pack, emptyProgress(), 's1', '  OHYB ŘEKY! ', DURING_GAME)
    expect(correct.result).toMatchObject({
      correct: true,
      rewardText: 'Odměna Brána',
      nextText: 'Jdi na Věž',
      nextStationId: 's2',
      gameCompleted: false,
    })

    const atTower = await solve(pack, emptyProgress(), 's1', 'ohyb řeky')
    expect((await localAnswer(pack, atTower, 's2', 'padesát šest', DURING_GAME)).result.message).toBe(NOT_A_NUMBER_MESSAGE)
    expect((await localAnswer(pack, atTower, 's2', '56 schodů', DURING_GAME)).result.correct).toBe(true)
  })

  it('refuses a station that is not active', async () => {
    const pack = await game()
    await expect(localAnswer(pack, emptyProgress(), 's2', '56', DURING_GAME)).rejects.toThrow('není aktivní')
  })

  it('opens the next station, rewards and scrambled puzzle letters', async () => {
    const pack = await game()
    const progress = await solve(pack, emptyProgress(), 's1', 'ohyb reky')
    const state = await localState(pack, progress, DURING_GAME)

    expect(state.currentStation).toMatchObject({ id: 's2', orderNo: 2, title: 'Věž', taskType: 'NUMBER' })
    expect(state.solvedStations).toEqual([
      { id: 's1', orderNo: 1, title: 'Brána', rewardText: 'Odměna Brána', rewardAudioUrl: null, nextText: 'Jdi na Věž' },
    ])
    expect(state.puzzle).toEqual({ slots: [null, 'K'], latestSlot: 1 })
  })

  it('counts revealed hints into the penalty', async () => {
    const pack = await game()
    const { result, revealed } = await localHint(pack, emptyProgress(), 's1')
    expect(result).toEqual({
      revealedHints: [{ orderNo: 1, text: 'Nápověda 1', penaltySeconds: 60 }],
      hintsAvailable: 1,
      nextHintPenaltySeconds: 120,
      penaltySeconds: 60,
    })

    const progress = { ...emptyProgress(), hints: { s1: revealed + 1 } }
    expect((await localState(pack, progress, DURING_GAME)).penaltySeconds).toBe(180)
    await expect(localHint(pack, progress, 's1')).rejects.toThrow('Další nápověda už není')
  })

  it('finishes the game with the finale and total time', async () => {
    const pack = await game()
    let progress: LocalProgress = { ...emptyProgress(), hints: { s1: 1 } }
    progress = await solve(pack, progress, 's1', 'ohyb reky')
    progress = await solve(pack, progress, 's2', '56')
    const last = await localAnswer(pack, progress, 's3', 'Mistr kat', new Date('2026-10-31T10:40:00Z'))
    expect(last.result).toMatchObject({ correct: true, gameCompleted: true })
    expect(last.finished).toBe(true)
    progress = await solve(pack, progress, 's3', 'kat', new Date('2026-10-31T10:40:00Z'))

    const state = await localState(pack, progress, new Date('2026-10-31T11:00:00Z'))
    expect(state.status).toBe('COMPLETED')
    expect(state.currentStation).toBeNull()
    // 40 minutes of play + 60 s for the hint
    expect(state.finale).toEqual({ outroText: 'Konec hry', outroAudioUrl: null, totalSeconds: 2460, hintsUsed: 1 })
  })

  it('expires with the code', async () => {
    const pack = await game()
    const state = await localState(pack, emptyProgress(), new Date('2026-10-31T15:00:00Z'))

    expect(state.status).toBe('EXPIRED')
    expect(state.currentStation).toBeNull()
  })
})
