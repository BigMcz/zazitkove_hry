import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client.ts'

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      // By default TanStack Query pauses requests while the browser is offline. The game plays from the downloaded
      // package without a signal (offline/playClient.ts), so it must run; without a package a request fails at once.
      queries: {
        networkMode: 'always',
        // 4xx answers will not change by retrying; network errors and 5xx get two more tries.
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
      },
      mutations: {
        networkMode: 'always',
        retry: false,
      },
    },
  })
}
