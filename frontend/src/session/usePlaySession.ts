import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router'
import { isSessionGone } from '../api/client.ts'
import { useSessionState } from '../api/queries.ts'
import { useSessionToken } from './sessionTokenContext.ts'

export interface HomeLocationState {
  message?: string
}

/**
 * State of the running game for the /play pages. Without a token, or when the server says the
 * session is gone (401/410), the token is cleared and the player is sent to the home page.
 */
export function usePlaySession() {
  const { token, clearToken } = useSessionToken()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const query = useSessionState(token)
  const leaving = useRef(false)

  const leaveGame = useCallback(
    (message?: string) => {
      leaving.current = true
      clearToken()
      queryClient.removeQueries({ queryKey: ['session'] })
      const state: HomeLocationState = { message }
      navigate('/', { replace: true, state })
    },
    [clearToken, queryClient, navigate],
  )

  /** Handles an error of a game action; returns the message to show, or null when the game is gone. */
  const handleError = useCallback(
    (error: Error): string | null => {
      if (isSessionGone(error)) {
        leaveGame(error.message)
        return null
      }
      return error.message
    },
    [leaveGame],
  )

  useEffect(() => {
    if (token === null && !leaving.current) {
      navigate('/', { replace: true })
    }
  }, [token, navigate])

  useEffect(() => {
    if (isSessionGone(query.error)) {
      leaveGame(query.error.message)
    }
  }, [query.error, leaveGame])

  // A gone session redirects home, so it is not shown as an error on the page itself.
  const loadError = query.error && !isSessionGone(query.error) ? query.error : null

  return { token, query, loadError, leaveGame, handleError }
}
