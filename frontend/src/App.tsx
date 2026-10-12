import { Navigate, Outlet, Route, Routes } from 'react-router';
import { useAuth } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { RequireGuest } from './auth/RequireGuest';
import { Message } from './components/Message';
import { AccountPage } from './pages/AccountPage';
import { DeleteAccountPage } from './pages/DeleteAccountPage';
import { LoginPage } from './pages/LoginPage';
import { PasswordPage } from './pages/PasswordPage';
import { RegisterPage } from './pages/RegisterPage';

/** ルートの定義 (アーキテクチャ設計 3.3) */
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route element={<RequireGuest />}>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
        </Route>
        <Route element={<RequireAuth />}>
          <Route path="/account" element={<AccountPage />} />
          <Route path="/account/password" element={<PasswordPage />} />
          <Route path="/account/delete" element={<DeleteAccountPage />} />
        </Route>
        <Route path="/" element={<HomeRedirect />} />
        <Route path="*" element={<HomeRedirect />} />
      </Route>
    </Routes>
  );
}

function Layout() {
  const { notice } = useAuth();
  return (
    <main>
      <Message kind="error" message={notice} />
      <Outlet />
    </main>
  );
}

/** `/` とそれ以外のパス。ログイン中なら /account、未ログインなら /login へ移る */
function HomeRedirect() {
  const { status } = useAuth();
  if (status === 'checking') {
    return null;
  }
  return <Navigate to={status === 'authenticated' ? '/account' : '/login'} replace />;
}
