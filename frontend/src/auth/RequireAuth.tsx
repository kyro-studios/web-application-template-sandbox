import { Navigate, Outlet } from 'react-router';
import { useAuth } from './AuthContext';

/** ログイン中でなければ /login へ移る。確認中は画面を出さずに待つ */
export function RequireAuth() {
  const { status, loginRedirectState } = useAuth();

  if (status === 'checking') {
    return null;
  }
  if (status !== 'authenticated') {
    // E-06 で未ログインになった場合は、E-06 のメッセージを /login へ引き継ぐ
    return <Navigate to="/login" replace state={loginRedirectState} />;
  }
  return <Outlet />;
}
