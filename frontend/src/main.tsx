import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App.tsx'
import '@fontsource-variable/outfit'
import './index.css'
import { createQueryClient } from './queryClient.ts'
import { registerServiceWorker } from './serviceWorker.ts'
import SessionTokenProvider from './session/SessionTokenProvider.tsx'

const queryClient = createQueryClient()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <SessionTokenProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </SessionTokenProvider>
    </QueryClientProvider>
  </StrictMode>,
)

registerServiceWorker()
