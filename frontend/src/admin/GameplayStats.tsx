import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type GameStats } from './adminApi.ts'
import { formatDuration, formatNumber, formatShare } from './format.ts'
import Section from './Section.tsx'
import Tabs from './Tabs.tsx'
import { adminKeys, useLoggedOutOn401 } from './shared.ts'

interface GameplayStatsProps {
  year: number
  /** Only games of this partner (the partner name is then hidden); all games when missing. */
  partnerId?: string
}

/** Names of the languages of the games in the administration. */
const LANGUAGES: Record<string, string> = { cs: 'čeština', de: 'němčina' }

function languageName(language: string): string {
  return LANGUAGES[language] ?? language
}

/** The language versions of one game (one card) together. */
interface MainGame {
  id: string
  title: string
  partnerName: string
  versions: GameStats[]
}

function byMainGame(rows: GameStats[]): MainGame[] {
  const games = new Map<string, MainGame>()
  for (const row of rows) {
    const game = games.get(row.mainGameId)
    if (game) {
      game.versions.push(row)
    } else {
      games.set(row.mainGameId, { id: row.mainGameId, title: row.mainGameTitle, partnerName: row.partnerName, versions: [row] })
    }
  }
  return [...games.values()]
}

/**
 * How the games are played: completion, duration and where the teams struggle. Only for the operator.
 * One game at a time, chosen by a tab; a game played in more languages shows how many games were played in each
 * and the figures of one language at a time.
 */
export default function GameplayStats({ year, partnerId }: GameplayStatsProps) {
  const stats = useQuery({
    queryKey: adminKeys.gameplay(year, partnerId),
    queryFn: () => adminApi.gameplay(year, partnerId),
  })
  useLoggedOutOn401(stats.error)
  const [selected, setSelected] = useState<string | null>(null)
  const games = byMainGame(stats.data ?? [])
  const game = games.find((candidate) => candidate.id === selected) ?? games[0]

  return (
    <Section title={`Jak se hraje ${year}`}>
      <p className="text-stone-700">
        Hry spuštěné v roce {year} kódy z prodaných kartiček, bez vývojových kódů. Doba hraní jen u dohraných her.
      </p>
      {stats.isPending && <p role="status">Načítám…</p>}
      {stats.isError && <ErrorBanner message={stats.error.message} onRetry={() => void stats.refetch()} />}
      {stats.data?.length === 0 && <p>V roce {year} zatím nikdo nehrál.</p>}
      {game && games.length === 1 && <Versions key={game.id} game={game} showPartner={!partnerId} />}
      {game && games.length > 1 && (
        <Tabs
          label="Hry"
          tabs={games.map((candidate) => ({
            id: candidate.id,
            label: partnerId ? candidate.title : `${candidate.title} · ${candidate.partnerName}`,
          }))}
          active={game.id}
          onChange={setSelected}
        >
          <Versions key={game.id} game={game} showPartner={false} />
        </Tabs>
      )}
    </Section>
  )
}

function Versions({ game, showPartner }: { game: MainGame; showPartner: boolean }) {
  const [language, setLanguage] = useState<string | null>(null)
  if (game.versions.length === 1) {
    return <Game game={game.versions[0]} showPartner={showPartner} />
  }
  const version = game.versions.find((candidate) => candidate.language === language) ?? game.versions[0]
  const started = game.versions.reduce((sum, candidate) => sum + candidate.started, 0)

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-semibold text-stone-600">Jazyk hry:</span>
        {game.versions.map((candidate) => (
          <button
            key={candidate.gameId}
            type="button"
            aria-pressed={candidate === version}
            onClick={() => setLanguage(candidate.language)}
            className={`min-h-9 rounded-full border px-3 text-sm font-semibold ${
              candidate === version ? 'border-stone-900 bg-stone-900 text-white' : 'border-stone-300 hover:border-stone-500'
            }`}
          >
            {languageName(candidate.language)} {formatNumber(candidate.started)} ({formatShare(candidate.started, started)})
          </button>
        ))}
      </div>
      <Game game={version} showPartner={showPartner} />
    </div>
  )
}

function Game({ game, showPartner }: { game: GameStats; showPartner: boolean }) {
  const figures = [
    { label: 'Spuštěno', value: formatNumber(game.started) },
    {
      label: 'Dohráno',
      value: formatNumber(game.completed),
      hint: `${formatShare(game.completed, game.started - game.playing)} ze skončených her`,
    },
    { label: 'Nedohráno', value: formatNumber(game.unfinished), hint: 'opuštěno nebo vypršelo' },
    { label: 'Právě hraje', value: formatNumber(game.playing) },
    {
      label: 'Doba hraní',
      value: formatDuration(game.durationMedianSeconds),
      hint:
        game.durationP25Seconds === null
          ? undefined
          : `polovina týmů ${formatDuration(game.durationP25Seconds)} až ${formatDuration(game.durationP75Seconds)}`,
    },
    {
      label: 'Výsledný čas',
      value: formatDuration(game.durationWithPenaltyMedianSeconds),
      hint: 'včetně penalizace za nápovědy',
    },
  ]

  return (
    <article aria-label={game.gameTitle} className="flex flex-col gap-3">
      <h3 className="text-lg font-bold">
        {game.gameTitle}
        {showPartner && <span className="font-normal text-stone-600"> · {game.partnerName}</span>}
      </h3>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {figures.map((figure) => (
          <div key={figure.label} className="flex flex-col gap-1 rounded-xl border border-stone-300 p-3">
            <dt className="text-sm font-semibold text-stone-600">{figure.label}</dt>
            <dd className="text-xl font-bold tabular-nums">{figure.value}</dd>
            {figure.hint && <dd className="text-xs text-stone-500">{figure.hint}</dd>}
          </div>
        ))}
      </dl>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Stanoviště hry {game.gameTitle}</caption>
          <thead className="border-b border-stone-300 text-stone-700">
            <tr>
              <th className="py-2 pr-3">Stanoviště</th>
              <th className="py-2 pr-3 text-right">Došlo</th>
              <th className="py-2 pr-3 text-right">Vyřešilo</th>
              <th className="py-2 pr-3 text-right">Skončilo tu</th>
              <th className="py-2 pr-3 text-right">Čas (medián)</th>
              <th className="py-2 pr-3 text-right">Špatných odpovědí na tým</th>
              <th className="py-2 text-right">S nápovědou</th>
            </tr>
          </thead>
          <tbody>
            {game.stations.map((station) => (
              <tr key={station.stationId} className="border-b border-stone-200">
                <td className="py-2 pr-3">
                  {station.orderNo}. {station.title}
                </td>
                <td className="py-2 pr-3 text-right">{formatNumber(station.reached)}</td>
                <td className="py-2 pr-3 text-right">{formatNumber(station.solved)}</td>
                <td className={`py-2 pr-3 text-right ${station.stoppedHere > 0 ? 'font-bold text-amber-800' : ''}`}>
                  {formatNumber(station.stoppedHere)}
                </td>
                <td className="py-2 pr-3 text-right whitespace-nowrap">{formatDuration(station.medianSeconds)}</td>
                <td className="py-2 pr-3 text-right">
                  {station.reached === 0 ? '—' : formatNumber(Math.round((station.wrongAttempts / station.reached) * 10) / 10)}
                </td>
                <td className="py-2 text-right">{formatShare(station.teamsWithHint, station.reached)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </article>
  )
}
