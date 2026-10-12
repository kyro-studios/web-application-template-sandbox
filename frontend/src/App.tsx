import { Outlet, Route, Routes } from 'react-router'

export function App() {
  return (
    <Routes>
      <Route element={<RootLayout />}>
        <Route path="/" element={<HomePage />} />
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
