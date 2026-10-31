import type {
  AnswerResult,
  CurrentStation,
  HintsResult,
  Puzzle,
  SessionState,
  SolvedStation,
} from '../api/types.ts'
import { openStation, solveStation } from './packageCrypto.ts'
import type { LocalProgress } from './storage.ts'
import { normalize, normalizeNumber } from './textNormalizer.ts'
import type { GamePackage, StationContent, Unlocked } from './types.ts'

/**
 * Game rules in the phone, played from the sealed package (docs/02-architektura.md, "Offline režim"). Gives the
 * same SessionState, AnswerResult and HintsResult as the server, so the game pages work the same way.
 */

// The same texts as the backend SessionService
export const WRONG_ANSWER_MESSAGE = 'To není ono. Zkus to znovu.'
export const NOT_A_NUMBER_MESSAGE = 'Odpověď musí obsahovat číslo.'

/** The game opened as far as the progress goes: content of every reached station and what the solved ones revealed. */
interface OpenedGame {
  /** contents[i] = content of the i-th station, known up to the first unsolved one. */
  contents: StationContent[]
  unlocked: Unlocked[]
}

async function openGame(pack: GamePackage, progress: LocalProgress): Promise<OpenedGame> {
  const contents = [pack.firstStation]
  const unlocked: Unlocked[] = []
  for (const solved of progress.solved) {
    const station = pack.stations.find((sealed) => sealed.id === solved.stationId)
    if (!station) {
      break
    }
    const opened = await openStation(station, solved.rawKey)
    unlocked.push(opened)
    if (opened.next) {
      contents.push(opened.next)
    }
  }
  return { contents, unlocked }
}

function revealedCount(progress: LocalProgress, station: StationContent): number {
  return Math.min(progress.hints[station.id] ?? 0, station.hints.length)
}

function penaltySeconds(progress: LocalProgress, contents: StationContent[]): number {
  return contents.reduce(
    (sum, station) =>
      sum + station.hints.slice(0, revealedCount(progress, station)).reduce((s, hint) => s + hint.penaltySeconds, 0),
    0,
  )
}

function currentStation(progress: LocalProgress, station: StationContent): CurrentStation {
  const revealed = revealedCount(progress, station)
  return {
    id: station.id,
    orderNo: station.orderNo,
    title: station.title,
    storyText: station.storyText,
    storyAudioUrl: station.storyAudioUrl,
    listenOnly: station.listenOnly,
    imageUrl: station.imageUrl,
    imageAlt: station.imageAlt,
    taskText: station.taskText,
    taskType: station.taskType,
    choices: station.choices,
    hintsAvailable: station.hints.length - revealed,
    nextHintPenaltySeconds: station.hints[revealed]?.penaltySeconds ?? null,
    revealedHints: station.hints.slice(0, revealed),
  }
}

function puzzle(pack: GamePackage, progress: LocalProgress, unlocked: Unlocked[]): Puzzle | null {
  if (!pack.puzzleSlots) {
    return null
  }
  const slots: (string | null)[] = Array.from({ length: Object.keys(pack.puzzleSlots).length }, () => null)
  let latestSlot: number | null = null
  progress.solved.forEach((solved, index) => {
    const slot = pack.puzzleSlots?.[solved.stationId]
    const letter = unlocked[index]?.puzzleLetter
    if (slot !== undefined && letter) {
      slots[slot] = letter
      latestSlot = index === progress.solved.length - 1 ? slot : latestSlot
    }
  })
  return { slots, latestSlot }
}

/** The state of the game as the server would give it; EXPIRED once the code is no longer valid. */
export async function localState(pack: GamePackage, progress: LocalProgress, now: Date): Promise<SessionState> {
  const { contents, unlocked } = await openGame(pack, progress)
  const finished = progress.finishedAt !== null
  const expired = !finished && now.getTime() >= new Date(pack.session.expiresAt).getTime()
  const penalty = penaltySeconds(progress, contents)
  const solvedStations: SolvedStation[] = unlocked.map((opened, index) => ({
    id: contents[index].id,
    orderNo: contents[index].orderNo,
    title: contents[index].title,
    rewardText: opened.rewardText,
    rewardAudioUrl: opened.rewardAudioUrl,
    nextText: opened.nextText,
  }))
  const finale = unlocked.at(-1)?.finale
  const current = contents[unlocked.length]

  return {
    status: finished ? 'COMPLETED' : expired ? 'EXPIRED' : 'IN_PROGRESS',
    game: { id: pack.gameId, title: pack.session.gameTitle },
    teamName: pack.session.teamName,
    startedAt: pack.session.startedAt,
    penaltySeconds: penalty,
    progress: { solved: unlocked.length, total: pack.stations.length },
    currentStation: finished || expired || !current ? null : currentStation(progress, current),
    solvedStations,
    puzzle: puzzle(pack, progress, unlocked),
    finale:
      finished && finale && progress.finishedAt
        ? {
            outroText: finale.outroText,
            outroAudioUrl: finale.outroAudioUrl,
            totalSeconds:
              Math.round(
                (new Date(progress.finishedAt).getTime() - new Date(pack.session.startedAt).getTime()) / 1000,
              ) + penalty,
            hintsUsed: contents.reduce((sum, station) => sum + revealedCount(progress, station), 0),
          }
        : null,
  }
}

/** The station the player is on, or null when the game is finished. */
async function activeStation(pack: GamePackage, progress: LocalProgress): Promise<StationContent | null> {
  if (progress.finishedAt !== null) {
    return null
  }
  const { contents, unlocked } = await openGame(pack, progress)
  return contents[unlocked.length] ?? null
}

export class LocalGameError extends Error {}

/**
 * Evaluates an answer in the phone. A correct answer returns the change to store: the solved station with its key
 * (and the end of the game after the last one).
 */
export async function localAnswer(
  pack: GamePackage,
  progress: LocalProgress,
  stationId: string,
  rawAnswer: string,
  now: Date,
): Promise<{ result: AnswerResult; solved: LocalProgress['solved'][number] | null; finished: boolean }> {
  const station = await activeStation(pack, progress)
  if (!station || station.id !== stationId) {
    throw new LocalGameError('Toto stanoviště teď není aktivní.')
  }
  const isNumber = station.taskType === 'NUMBER'
  const normalized = isNumber ? normalizeNumber(rawAnswer) : normalize(rawAnswer)
  const sealed = pack.stations.find((candidate) => candidate.id === stationId)
  const solved = sealed && normalized !== '' ? await solveStation(sealed, normalized, pack.kdfIterations) : null
  if (!solved) {
    const message = isNumber && normalized === '' ? NOT_A_NUMBER_MESSAGE : WRONG_ANSWER_MESSAGE
    return { result: { correct: false, message }, solved: null, finished: false }
  }
  const { unlocked } = solved
  return {
    result: {
      correct: true,
      rewardText: unlocked.rewardText,
      rewardAudioUrl: unlocked.rewardAudioUrl ?? undefined,
      nextText: unlocked.nextText ?? undefined,
      nextStationId: unlocked.next?.id,
      gameCompleted: unlocked.next === null,
    },
    solved: { stationId, rawKey: solved.rawKey, solvedAt: now.toISOString() },
    finished: unlocked.next === null,
  }
}

/** Reveals the next hint of the active station in the phone; returns the result and the new count of hints. */
export async function localHint(
  pack: GamePackage,
  progress: LocalProgress,
  stationId: string,
): Promise<{ result: HintsResult; revealed: number }> {
  const station = await activeStation(pack, progress)
  if (!station || station.id !== stationId) {
    throw new LocalGameError('Toto stanoviště teď není aktivní.')
  }
  const revealed = revealedCount(progress, station)
  if (revealed >= station.hints.length) {
    throw new LocalGameError('Další nápověda už není.')
  }
  const after: LocalProgress = { ...progress, hints: { ...progress.hints, [stationId]: revealed + 1 } }
  const { contents } = await openGame(pack, after)
  return {
    result: {
      revealedHints: station.hints.slice(0, revealed + 1),
      hintsAvailable: station.hints.length - revealed - 1,
      nextHintPenaltySeconds: station.hints[revealed + 1]?.penaltySeconds ?? null,
      penaltySeconds: penaltySeconds(after, contents),
    },
    revealed: revealed + 1,
  }
}
