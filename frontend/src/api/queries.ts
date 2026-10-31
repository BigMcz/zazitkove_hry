import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { playClient } from '../offline/playClient.ts'
import { api } from './client.ts'

export const queryKeys = {
  session: (token: string | null) => ['session', token] as const,
}

export function useSessionState(token: string | null) {
  return useQuery({
    queryKey: queryKeys.session(token),
    queryFn: () => playClient.getState(token as string),
    enabled: token !== null,
  })
}

export function useStartSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ code, teamName }: { code: string; teamName: string | null }) => api.startSession(code, teamName),
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
      playClient.submitAnswer(token as string, stationId, answer),
  })
}

export function useRevealHint(token: string | null) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (stationId: string) => playClient.revealHint(token as string, stationId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.session(token) }),
  })
}
