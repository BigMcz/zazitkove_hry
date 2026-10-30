import { QueryClientProvider } from '@tanstack/react-query'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import HomePage from './pages/HomePage.tsx'
import { createQueryClient } from './queryClient.ts'
import SessionTokenProvider from './session/SessionTokenProvider.tsx'

/**
 * Landing page as HTML for dist/index.html (prerender.mjs after vite build): search engines and link
 * previews see the content without running JavaScript. The app (main.tsx) then renders over it.
 */
export function render(): string {
  return renderToString(
    <QueryClientProvider client={createQueryClient()}>
      <SessionTokenProvider>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </SessionTokenProvider>
    </QueryClientProvider>,
  )
}
