import { useQuery } from '@tanstack/react-query'
import ErrorBanner from '../components/ErrorBanner.tsx'
import { adminApi, type GameStats } from './adminApi.ts'
import { formatDuration, formatNumber, formatShare } from './format.ts'
import Section from './Section.tsx'
import { adminKeys, useLoggedOutOn401 } from './shared.ts'

interface GameplayStatsProps {
  year: number
  /** Only games of this partner (the partner name is then hidden); all games when missing. */
  partnerId?: string
}

/** How the games are played: completion, duration and where the teams struggle. Only for the operator. */
export default function GameplayStats({ year, partnerId }: GameplayStatsProps) {
  const stats = useQuery({
    queryKey: adminKeys.gameplay(year, partnerId),
    queryFn: () => adminApi.gameplay(year, partnerId),
  })
  useLoggedOutOn401(stats.error)

  return (
    <Section title={`Jak se hraje ${year}`}>
      <p className="text-stone-700">
        Hry spuštěné v roce {year} kódy z prodaných kartiček, bez vývojových kódů. Doba hraní jen u dohraných her.
      </p>
      {stats.isPending && <p role="status">Načítám…</p>}
      {stats.isError && <ErrorBanner message={stats.error.message} onRetry={() => void stats.refetch()} />}
      {stats.data?.length === 0 && <p>V roce {year} zatím nikdo nehrál.</p>}
      {stats.data?.map((game) => (
        <Game key={game.gameId} game={game} showPartner={!partnerId} />
      ))}
    </Section>
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
    <article aria-label={game.gameTitle} className="flex flex-col gap-3 border-t border-stone-200 pt-4 first-of-type:border-t-0">
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
