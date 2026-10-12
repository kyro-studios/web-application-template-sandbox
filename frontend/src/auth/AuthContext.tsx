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
import { ApiError, setUnauthorizedHandler } from '../api/client';
import type { Account } from '../api/types';

/**
 * ログイン状態。checking は起動時の GET /api/me の結果を待っている間。
 * error は GET /api/me が E-06 以外の異常 (E-90、通信の失敗) で終わり、ログイン状態が分からない状態
 */
export type AuthStatus = 'checking' | 'unauthenticated' | 'authenticated' | 'error';

/** E-06 や操作の成功 (OP-7 など) で /login へ移るときに渡す、画面の遷移の状態 */
export interface LoginRedirectState {
  /** 表示する E-06 のメッセージ (API の message のまま) */
  errorMessage?: string;
  /** 表示する成功のメッセージ (API の message のまま) */
  successMessage?: string;
}

export interface AuthContextValue {
  status: AuthStatus;
  /** ログイン中のアカウントの表示名とメールアドレス。ログイン中でなければ null */
  account: Account | null;
  /** E-06 や操作の成功で未ログインになったときの、/login へ移るときの状態。それ以外では null */
  loginRedirectState: LoginRedirectState | null;
  /** status が error のときの、表示するメッセージ (API の message のまま)。それ以外では null */
  checkErrorMessage: string | null;
  /** status が error のときに、GET /api/me を呼び直してログイン状態を確かめ直す */
  retryCheck: () => void;
  /** ログイン中にし、アカウントの情報を持つ (OP-1、OP-5 の成功など) */
  markAuthenticated: (account: Account) => void;
  /** 未ログインにする (OP-3、OP-7 の成功など)。loginRedirectState は /login へ移るときに引き継ぐ */
  markUnauthenticated: (loginRedirectState?: LoginRedirectState) => void;
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
  checkErrorMessage: string | null;
}

const CHECKING: AuthState = { status: 'checking', account: null, loginRedirectState: null, checkErrorMessage: null };

const UNAUTHENTICATED: AuthState = {
  status: 'unauthenticated',
  account: null,
  loginRedirectState: null,
  checkErrorMessage: null,
};

function authenticated(account: Account): AuthState {
  return { status: 'authenticated', account, loginRedirectState: null, checkErrorMessage: null };
}

function checkFailed(message: string): AuthState {
  return { status: 'error', account: null, loginRedirectState: null, checkErrorMessage: message };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [state, setState] = useState<AuthState>(CHECKING);
  // 増やすと、起動時の確認 (GET /api/me) をやり直す
  const [checkCount, setCheckCount] = useState(0);

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

  // 起動時に GET /api/me を呼ぶ。200 ならログイン中。
  // E-06 なら、ほかの API と同じく、上の処理で未ログインにして /login へ移り、E-06 のメッセージを表示する
  useEffect(() => {
    let active = true;
    getMe()
      .then(({ account }) => {
        if (active) {
          setState(authenticated(account));
        }
      })
      .catch((error: unknown) => {
        // E-06 の状態 (メッセージ付きの未ログイン) は上の処理で設定済みのため、消さない
        if (!active || (error instanceof ApiError && error.code === 'E-06')) {
          return;
        }
        // E-90 や通信の失敗では、ログイン状態が分からないため、メッセージと確かめ直す手段を出す
        const message = error instanceof ApiError ? error.message : String(error);
        setState(checkFailed(message));
      });
    return () => {
      active = false;
    };
  }, [checkCount]);

  const retryCheck = useCallback(() => {
    setState(CHECKING);
    setCheckCount((count) => count + 1);
  }, []);

  const markAuthenticated = useCallback((account: Account) => {
    setState(authenticated(account));
  }, []);

  const markUnauthenticated = useCallback((loginRedirectState?: LoginRedirectState) => {
    setState({ ...UNAUTHENTICATED, loginRedirectState: loginRedirectState ?? null });
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
      checkErrorMessage: state.checkErrorMessage,
      retryCheck,
      markAuthenticated,
      markUnauthenticated,
      refresh,
    }),
    [state, retryCheck, markAuthenticated, markUnauthenticated, refresh],
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
