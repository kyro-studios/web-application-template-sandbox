import { Navigate, Outlet } from 'react-router';
import { useAuth } from './AuthContext';

/** ログイン中なら /account へ移る。確認中は画面を出さずに待つ */
export function RequireGuest() {
  const { status } = useAuth();
  if (status === 'checking') {
    return null;
  }
  if (status === 'authenticated') {
    return <Navigate to="/account" replace />;
  }
  return <Outlet />;
}
