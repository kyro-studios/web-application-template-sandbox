import { Navigate, Route, Routes } from 'react-router';
import { useAuth } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { RequireGuest } from './auth/RequireGuest';
import { AccountPage } from './pages/AccountPage';
import { DeleteAccountPage } from './pages/DeleteAccountPage';
import { LoginPage } from './pages/LoginPage';
import { PasswordPage } from './pages/PasswordPage';
import { RegisterPage } from './pages/RegisterPage';

/** `/` とそれ以外のパス: ログイン中なら /account、未ログインなら /login へ移る */
function RootRedirect() {
  const { status } = useAuth();

  if (status === 'checking') {
    return null;
  }
  return <Navigate to={status === 'authenticated' ? '/account' : '/login'} replace />;
}

/** ルートの定義 (アーキテクチャ設計 3.3) */
export function App() {
  return (
    <Routes>
      <Route element={<RequireGuest />}>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route path="/account" element={<AccountPage />} />
        <Route path="/account/password" element={<PasswordPage />} />
        <Route path="/account/delete" element={<DeleteAccountPage />} />
      </Route>
      <Route path="/" element={<RootRedirect />} />
      <Route path="*" element={<RootRedirect />} />
    </Routes>
  );
}
