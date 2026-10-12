import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useNavigate } from 'react-router';
import { getMe } from '../api/auth';
import { setUnauthorizedHandler } from '../api/client';
import type { Account } from '../api/types';

/** ログイン状態。checking は起動時の GET /api/me の結果を待っている間 */
export type AuthStatus = 'checking' | 'unauthenticated' | 'authenticated';

/** E-06 で /login へ移るときに渡す、画面の遷移の状態 */
export interface LoginRedirectState {
  /** 表示する E-06 のメッセージ (API の message のまま) */
  errorMessage: string;
}

export interface AuthContextValue {
  status: AuthStatus;
  /** ログイン中のアカウントの表示名とメールアドレス。ログイン中でなければ null */
  account: Account | null;
  /** E-06 で未ログインになったときの、/login へ移るときの状態。それ以外では null */
  loginRedirectState: LoginRedirectState | null;
  /** ログイン中にし、アカウントの情報を持つ (OP-1、OP-5 の成功など) */
  markAuthenticated: (account: Account) => void;
  /** 未ログインにする (OP-3、OP-7 の成功など) */
  markUnauthenticated: () => void;
  /**
   * GET /api/me を呼び直して、ログイン状態とアカウントの情報を更新する。
   * OP-2 の応答にはメールアドレスが含まれないため、ログインの後に使う
   */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

interface AuthState {
  status: AuthStatus;
  account: Account | null;
  loginRedirectState: LoginRedirectState | null;
}

const UNAUTHENTICATED: AuthState = { status: 'unauthenticated', account: null, loginRedirectState: null };

function authenticated(account: Account): AuthState {
  return { status: 'authenticated', account, loginRedirectState: null };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [state, setState] = useState<AuthState>({
    status: 'checking',
    account: null,
    loginRedirectState: null,
  });

  // 起動時に GET /api/me を呼ぶ。200 ならログイン中、E-06 なら未ログイン
  useEffect(() => {
    let active = true;
    getMe({ skipUnauthorizedHandler: true })
      .then(({ account }) => {
        if (active) {
          setState(authenticated(account));
        }
      })
      .catch(() => {
        // E-06 は未ログイン。E-90 などでログイン状態が分からない場合も、未ログインとして扱う
        if (active) {
          setState(UNAUTHENTICATED);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  // どの API でも E-06 を受け取ったら、未ログインにして /login へ移り、E-06 のメッセージを表示する。
  // 画面の遷移より先に RequireAuth が /login へ移すことがあるため、遷移の状態は AuthContext にも持たせる
  useEffect(
    () =>
      setUnauthorizedHandler((error) => {
        const loginRedirectState: LoginRedirectState = { errorMessage: error.message };
        setState({ ...UNAUTHENTICATED, loginRedirectState });
        navigate('/login', { replace: true, state: loginRedirectState });
      }),
    [navigate],
  );

  const markAuthenticated = useCallback((account: Account) => {
    setState(authenticated(account));
  }, []);

  const markUnauthenticated = useCallback(() => {
    setState(UNAUTHENTICATED);
  }, []);

  const refresh = useCallback(async () => {
    const { account } = await getMe();
    setState(authenticated(account));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status: state.status,
      account: state.account,
      loginRedirectState: state.loginRedirectState,
      markAuthenticated,
      markUnauthenticated,
      refresh,
    }),
    [state, markAuthenticated, markUnauthenticated, refresh],
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
