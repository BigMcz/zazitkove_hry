import { useLayoutEffect } from 'react'
import { Link, Route, Routes, useLocation } from 'react-router'
import AdminPage from './admin/AdminPage.tsx'
import Page from './components/Page.tsx'
import { linkSubtle } from './components/ui.ts'
import FinalePage from './pages/FinalePage.tsx'
import HomePage from './pages/HomePage.tsx'
import PrivacyPage from './pages/PrivacyPage.tsx'
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
    <Page>
      <h1 className="font-serif text-3xl font-bold">Stránka nenalezena</h1>
      <Link to="/" className={linkSubtle}>
        Zpět na úvod
      </Link>
    </Page>
  )
}
