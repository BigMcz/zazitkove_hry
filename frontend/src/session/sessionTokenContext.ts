import { createContext, useContext } from 'react'

export interface SessionTokenContextValue {
  token: string | null
  setToken: (token: string) => void
  clearToken: () => void
}

export const SessionTokenContext = createContext<SessionTokenContextValue | null>(null)

export function useSessionToken(): SessionTokenContextValue {
  const value = useContext(SessionTokenContext)
  if (value === null) {
    throw new Error('useSessionToken must be used inside SessionTokenProvider')
  }
  return value
}
