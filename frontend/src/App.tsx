import { useLayoutEffect } from 'react'
import { Link, Route, Routes, useLocation } from 'react-router'
import AdminPage from './admin/AdminPage.tsx'
import SiteLayout from './components/SiteLayout.tsx'
import FinalePage from './pages/FinalePage.tsx'
import HomePage from './pages/HomePage.tsx'
import PrivacyPage from './pages/PrivacyPage.tsx'
import RulesPage from './pages/RulesPage.tsx'
import RewardPage from './pages/RewardPage.tsx'
import StartPage from './pages/StartPage.tsx'
import StationPage from './pages/StationPage.tsx'

export default function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/ochrana-udaju" element={<PrivacyPage />} />
        <Route path="/pravidla" element={<RulesPage />} />
        <Route path="/start" element={<StartPage />} />
        <Route path="/play" element={<StationPage />} />
        <Route path="/play/reward" element={<RewardPage />} />
        <Route path="/play/finale" element={<FinalePage />} />
        <Route path="/admin/*" element={<AdminPage />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </>
  )
}

/** Every page (next station, reward, finale) starts at the top, not where the previous one was scrolled. */
function ScrollToTop() {
  const { pathname } = useLocation()
  useLayoutEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])
  return null
}

function NotFound() {
  return (
    <SiteLayout>
      <title>Stránka nenalezena | StoryHold</title>
      <main className="flex flex-col items-start gap-6 py-20 md:py-28">
        <p className="text-xs font-bold tracking-[0.18em] text-lime uppercase">Chyba 404</p>
        <h1 className="text-4xl font-extrabold tracking-tight sm:text-5xl">Stránka nenalezena</h1>
        <p className="max-w-xl text-lg leading-relaxed text-stone-300">
          Tady žádná stopa nevede. Adresa je možná překlepnutá nebo stránka už neexistuje.
        </p>
        <Link
          to="/"
          className="inline-flex min-h-12 items-center rounded-full bg-lime px-6 font-bold text-night transition-colors hover:bg-lime-dark"
        >
          Zpět na úvod
        </Link>
      </main>
    </SiteLayout>
  )
}
