import { useMemo, useState, type ReactNode } from 'react'
import { SessionTokenContext } from './sessionTokenContext.ts'
import { forgetOfflineStatus } from '../offline/gameDownload.ts'
import { clearOfflineGame } from '../offline/storage.ts'
import { tokenStorage } from './tokenStorage.ts'

export default function SessionTokenProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => tokenStorage.get())

  const value = useMemo(
    () => ({
      token,
      setToken: (newToken: string) => {
        tokenStorage.set(newToken)
        setTokenState(newToken)
      },
      clearToken: () => {
        tokenStorage.clear()
        // The downloaded game belongs to the session
        clearOfflineGame()
        forgetOfflineStatus()
        setTokenState(null)
      },
    }),
    [token],
  )

  return <SessionTokenContext value={value}>{children}</SessionTokenContext>
}
