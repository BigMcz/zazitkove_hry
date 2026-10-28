import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client.ts'

export const queryKeys = {
  games: ['games'] as const,
  game: (gameId: string) => ['games', gameId] as const,
  session: (token: string | null) => ['session', token] as const,
}

export function useGames() {
  return useQuery({ queryKey: queryKeys.games, queryFn: api.listGames })
}

export function useGame(gameId: string) {
  return useQuery({ queryKey: queryKeys.game(gameId), queryFn: () => api.getGame(gameId) })
}

export function useSessionState(token: string | null) {
  return useQuery({
    queryKey: queryKeys.session(token),
    queryFn: () => api.getSession(token as string),
    enabled: token !== null,
  })
}

export function useStartSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ gameId, teamName }: { gameId: string; teamName: string | null }) =>
      api.startSession(gameId, teamName),
    onSuccess: (state) => {
      if (state.token) {
        queryClient.setQueryData(queryKeys.session(state.token), state)
      }
    },
  })
}

/**
 * Deliberately does not refresh the session state: after a correct answer the station page would
 * see the next station (or COMPLETED) and redirect before the reward is shown. The reward page
 * refetches the (always stale) session state when it mounts.
 */
export function useSubmitAnswer(token: string | null) {
  return useMutation({
    mutationFn: ({ stationId, answer }: { stationId: string; answer: string }) =>
      api.submitAnswer(token as string, stationId, answer),
  })
}

export function useRevealHint(token: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (stationId: string) => api.revealHint(token as string, stationId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.session(token) }),
  })
}
