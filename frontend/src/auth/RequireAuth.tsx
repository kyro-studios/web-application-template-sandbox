import { Navigate, Outlet } from 'react-router';
import { useAuth } from './AuthContext';

/** ログイン中でなければ /login へ移る。確認中は画面を出さずに待つ */
export function RequireAuth() {
  const { status } = useAuth();
  if (status === 'checking') {
    return null;
  }
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
