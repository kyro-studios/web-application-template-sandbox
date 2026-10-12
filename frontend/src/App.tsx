import { Outlet, Route, Routes } from 'react-router'
import { AnnouncementsPage } from './announcements/AnnouncementsPage'

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
      {/* 共通のナビゲーションは、この位置に置く */}
      <main>
        <Outlet />
      </main>
    </>
  )
}

function HomePage() {
  return <h1>Webアプリケーション</h1>
}
