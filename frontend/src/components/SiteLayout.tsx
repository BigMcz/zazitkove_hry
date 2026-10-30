import { useEffect, type ReactNode } from 'react'
import { Link } from 'react-router'
import logoUrl from '../assets/storyhold.svg'
import { operator } from './operator.ts'

/** Dark layout of the presentation pages (landing page, privacy policy) with the header and footer. */
export default function SiteLayout({ children }: { children: ReactNode }) {
  // Dark page also behind the content (overscroll on phones); the game pages keep the light body
  useEffect(() => {
    document.body.classList.add('bg-night')
    return () => document.body.classList.remove('bg-night')
  }, [])

  return (
    <div className="min-h-dvh bg-night font-display text-white">
      <div className="mx-auto flex w-full max-w-6xl flex-col px-5">
        <SiteHeader />
        {children}
        <SiteFooter />
      </div>
    </div>
  )
}

function SiteHeader() {
  return (
    <header className="flex items-center justify-between gap-4 border-b border-night-line py-5">
      {/* On "/" the router alone would not scroll back to the top */}
      <Link
        to="/"
        onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
        className="rounded focus-visible:outline-3 focus-visible:outline-offset-4 focus-visible:outline-lime"
      >
        <img src={logoUrl} alt="StoryHold, úvodní stránka" className="h-7 w-auto" />
      </Link>
      <span className="text-xs font-bold tracking-[0.18em] text-lime uppercase">Historie Jinak</span>
    </header>
  )
}

function SiteFooter() {
  return (
    <footer className="flex flex-col gap-8 border-t border-night-line py-8 sm:flex-row sm:items-end sm:justify-between">
      <div className="flex flex-col gap-3">
        <img src={logoUrl} alt="StoryHold" className="h-6 w-fit" />
        <span className="text-sm text-stone-400">Historie čeká na další tah.</span>
        <Link to="/ochrana-udaju" className="w-fit text-xs text-stone-400 underline hover:text-lime">
          Provozovatel a ochrana osobních údajů
        </Link>
      </div>
      <address className="flex flex-col gap-1 text-sm not-italic sm:items-end">
        <h2 className="mb-1 text-xs font-bold tracking-[0.18em] text-lime uppercase">Kontakt</h2>
        <span className="font-bold">{operator.name}</span>
        <a href={`tel:${operator.phone.replaceAll(' ', '')}`} className="text-stone-300 hover:text-lime">
          {operator.phone}
        </a>
        <a href={`mailto:${operator.email}`} className="text-stone-300 hover:text-lime">
          {operator.email}
        </a>
      </address>
    </footer>
  )
}
