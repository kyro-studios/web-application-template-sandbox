import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { getMe } from '../api/auth';
import { ApiError, setUnauthorizedHandler } from '../api/client';
import type { Account } from '../api/types';

/** ログイン状態。起動時の GET /api/me の結果が出るまでは checking */
export type AuthStatus = 'checking' | 'unauthenticated' | 'authenticated';

type AuthState =
  | { status: 'checking'; account: null }
  | { status: 'unauthenticated'; account: null }
  | { status: 'authenticated'; account: Account };

export interface AuthContextValue {
  status: AuthStatus;
  /** ログイン中のアカウントの表示名とメールアドレス。ログイン中でなければ null */
  account: Account | null;
  /** E-06 などで /login へ移ったときに表示する、API の応答のメッセージ */
  notice: string | null;
  /** ログイン中にする。表示名とメールアドレスを置き換える場合にも使う */
  setAuthenticated: (account: Account) => void;
  /** 未ログインにする */
  setUnauthenticated: () => void;
}

const UNAUTHENTICATED: AuthState = { status: 'unauthenticated', account: null };

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'checking', account: null });
  const [notice, setNotice] = useState<string | null>(null);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  // 起動時に GET /api/me でログイン状態を確かめる。200 ならログイン中、E-06 なら未ログイン
  useEffect(() => {
    let active = true;
    getMe({ handleUnauthorized: false }).then(
      ({ account }) => {
        if (active) {
          setState({ status: 'authenticated', account });
        }
      },
      (error: unknown) => {
        if (!active) {
          return;
        }
        setState(UNAUTHENTICATED);
        if (error instanceof ApiError && error.code !== 'E-06') {
          setNotice(error.message);
        }
      },
    );
    return () => {
      active = false;
    };
  }, []);

  // どの API でも E-06 を受け取ったら、未ログインにして /login へ移り、E-06 のメッセージを表示する
  useEffect(
    () =>
      setUnauthorizedHandler((error) => {
        setState(UNAUTHENTICATED);
        setNotice(error.message);
        navigate('/login', { replace: true });
      }),
    [navigate],
  );

  // /login 以外の画面へ移ったら、メッセージを消す
  const previousPathname = useRef(pathname);
  useEffect(() => {
    if (previousPathname.current === pathname) {
      return;
    }
    previousPathname.current = pathname;
    if (pathname !== '/login') {
      setNotice(null);
    }
  }, [pathname]);

  const setAuthenticated = useCallback((account: Account) => {
    setState({ status: 'authenticated', account });
    setNotice(null);
  }, []);

  const setUnauthenticated = useCallback(() => {
    setState(UNAUTHENTICATED);
    setNotice(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ status: state.status, account: state.account, notice, setAuthenticated, setUnauthenticated }),
    [state, notice, setAuthenticated, setUnauthenticated],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (value === null) {
    throw new Error('useAuth は AuthProvider の中で使ってください');
  }
  return value;
}
