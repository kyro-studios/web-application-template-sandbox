import { Navigate, Outlet } from 'react-router';
import { AuthCheckError } from './AuthCheckError';
import { useAuth } from './AuthContext';

/** ログイン中でなければ /login へ移る。確認中は画面を出さずに待ち、確認に失敗したら再試行の手段を出す */
export function RequireAuth() {
  const { status, loginRedirectState } = useAuth();

  if (status === 'checking') {
    return null;
  }
  if (status === 'error') {
    return <AuthCheckError />;
  }
  if (status !== 'authenticated') {
    // E-06 や操作の成功で未ログインになった場合は、そのメッセージを /login へ引き継ぐ
    return <Navigate to="/login" replace state={loginRedirectState} />;
  }
  return <Outlet />;
}
