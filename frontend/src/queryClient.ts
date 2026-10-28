import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client.ts'

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // 4xx answers will not change by retrying; network errors and 5xx get two more tries.
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 2,
      },
      mutations: {
        retry: false,
      },
    },
  })
}
