import { startTransition, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { getMe, login } from '../api/auth';
import { ApiError } from '../api/client';
import type { FieldErrorItem } from '../api/types';
import { useAuth, type LoginRedirectState } from '../auth/AuthContext';
import { FieldError } from '../components/FieldError';
import { Message } from '../components/Message';
import type { AccountPageState } from './AccountPage';

/** OP-2 ログイン */
export function LoginPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { markAuthenticated } = useAuth();
  const redirectState = location.state as Partial<LoginRedirectState> | null;
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorItem[]>([]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // パスワードの入力欄は、送信したら成功しても失敗しても空にする (NFR-3)。
    // メールアドレスは、異常のときも入力し直さずに済むように値を残す
    const input = { email, password };
    setPassword('');
    setSubmitting(true);
    setSubmitted(true);
    setErrorMessage(null);
    setFieldErrors([]);

    try {
      const { message } = await login(input);
      // OP-2 の応答にはメールアドレスが含まれないため、GET /api/me でログイン中のアカウントの情報を得る
      const { account } = await getMe();
      // 画面の遷移は transition で反映されるため、ログイン中にするのも同じ transition で行う。
      // 分けると、先にログイン中になった時点で RequireGuest が /account へ移し、message が渡らない
      const state: AccountPageState = { successMessage: message };
      startTransition(() => {
        markAuthenticated(account);
        navigate('/account', { replace: true, state });
      });
    } catch (error) {
      setSubmitting(false);
      if (error instanceof ApiError) {
        setErrorMessage(error.message);
        setFieldErrors(error.fieldErrors);
      } else {
        setErrorMessage(String(error));
      }
    }
  }

  return (
    <main>
      <h1>ログイン</h1>
      {/* E-06 で移ってきた場合は、送信するまで E-06 のメッセージを表示する。
          遷移の状態は後から届くことがあるため、初期値にせずに毎回読む */}
      <Message type="error" text={submitted ? errorMessage : redirectState?.errorMessage} />
      <form noValidate onSubmit={handleSubmit}>
        <div>
          <label htmlFor="login-email">メールアドレス</label>
          <input
            id="login-email"
            name="email"
            type="text"
            inputMode="email"
            autoComplete="username"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            aria-describedby="login-email-error"
          />
          <FieldError field="email" errors={fieldErrors} id="login-email-error" />
        </div>
        <div>
          <label htmlFor="login-password">パスワード</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-describedby="login-password-error"
          />
          <FieldError field="password" errors={fieldErrors} id="login-password-error" />
        </div>
        <button type="submit" disabled={submitting}>
          ログインする
        </button>
      </form>
      <p>
        アカウントをお持ちでない場合は <Link to="/register">利用者登録</Link>
      </p>
    </main>
  );
}
