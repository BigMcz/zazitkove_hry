import { Link, Route, Routes } from 'react-router'
import Page from './components/Page.tsx'
import { linkSubtle } from './components/ui.ts'
import FinalePage from './pages/FinalePage.tsx'
import GameIntroPage from './pages/GameIntroPage.tsx'
import HomePage from './pages/HomePage.tsx'
import RewardPage from './pages/RewardPage.tsx'
import StationPage from './pages/StationPage.tsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/games/:gameId" element={<GameIntroPage />} />
      <Route path="/play" element={<StationPage />} />
      <Route path="/play/reward" element={<RewardPage />} />
      <Route path="/play/finale" element={<FinalePage />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
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
