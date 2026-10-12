import { Outlet, Route, Routes } from 'react-router'
import { AnnouncementsPage } from './announcements/AnnouncementsPage'
import { AppNavigation } from './components/AppNavigation'

export function App() {
  return (
    <Routes>
      <Route element={<RootLayout />}>
        <Route path="/" element={<HomePage />} />
        {/* ログインの有無に関係なく表示する。ガードは付けない */}
        <Route path="/announcements" element={<AnnouncementsPage />} />
      </Route>
    </Routes>
  )
}

// すべての画面に共通のレイアウト
function RootLayout() {
  return (
    <>
      <AppNavigation />
      <main>
        <Outlet />
      </main>
    </>
  )
}

function HomePage() {
  return <h1>Webアプリケーション</h1>
}
