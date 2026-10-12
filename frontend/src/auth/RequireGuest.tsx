import { Navigate, Outlet } from 'react-router';
import { AuthCheckError } from './AuthCheckError';
import { useAuth } from './AuthContext';

/** ログイン中なら /account へ移る。確認中は画面を出さずに待ち、確認に失敗したら再試行の手段を出す */
export function RequireGuest() {
  const { status } = useAuth();

  if (status === 'checking') {
    return null;
  }
  if (status === 'error') {
    return <AuthCheckError />;
  }
  if (status === 'authenticated') {
    return <Navigate to="/account" replace />;
  }
  return <Outlet />;
}
