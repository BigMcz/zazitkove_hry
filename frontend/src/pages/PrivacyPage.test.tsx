import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import PrivacyPage from './PrivacyPage.tsx'

describe('PrivacyPage', () => {
  it('names the controller and every value stored in the browser', () => {
    render(
      <MemoryRouter>
        <PrivacyPage />
      </MemoryRouter>,
    )
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Jak zacházíme s vašimi údaji')
    expect(screen.getAllByText(/IČO 03896471/).length).toBeGreaterThan(0)
    // Keep the table in sync with tokenStorage.ts, audioPreference.ts and offline/storage.ts
    expect(screen.getByText('zazitky.sessionToken')).toBeInTheDocument()
    expect(screen.getByText('zazitky.voiceMuted')).toBeInTheDocument()
    expect(screen.getByText('zazitky.gamePackage, zazitky.gameProgress, game-media')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Provozovatel a ochrana osobních údajů' })).toHaveAttribute('href', '/ochrana-udaju')
  })
})
