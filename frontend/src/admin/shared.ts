import { useQueryClient, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { ApiError } from '../api/client.ts'

export const adminKeys = {
  session: ['admin', 'session'] as const,
  games: ['admin', 'games'] as const,
  batches: (scope: Scope) => ['admin', 'batches', scope] as const,
  report: (scope: Scope, year: number) => ['admin', 'report', scope, year] as const,
  partners: ['admin', 'partners'] as const,
  ownShares: ['admin', 'own-shares'] as const,
  partnerUsers: ['admin', 'partner-users'] as const,
}

/** Which API a shared view reads from: the operator's (all partners) or the castle's (own data only). */
export type Scope = 'admin' | 'partner'

export const thisYear = Number(
  new Intl.DateTimeFormat('en', { year: 'numeric', timeZone: 'Europe/Prague' }).format(new Date()),
)

/**
 * After logout or an expired session: forget the loaded data and ask the server again who is
 * logged in. That shows the login form and also issues a new XSRF-TOKEN cookie (logout deletes it,
 * without it the next login would be rejected).
 */
export function endSession(queryClient: QueryClient): Promise<void> {
  queryClient.removeQueries({ queryKey: ['admin'], predicate: (query) => query.queryKey[1] !== 'session' })
  return queryClient.refetchQueries({ queryKey: adminKeys.session })
}

/** A 401 means the session expired: show the login form again. */
export function useLoggedOutOn401(error: Error | null) {
  const queryClient = useQueryClient()
  useEffect(() => {
    if (error instanceof ApiError && error.status === 401) {
      void endSession(queryClient)
    }
  }, [error, queryClient])
}

export const fieldLabel = 'flex flex-col gap-1 font-semibold'

export const linkButton = 'min-h-11 font-semibold underline underline-offset-4'
