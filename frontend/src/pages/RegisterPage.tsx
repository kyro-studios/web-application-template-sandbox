import { startTransition, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { register } from '../api/auth';
import { ApiError } from '../api/client';
import type { FieldErrorItem } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { FieldError } from '../components/FieldError';
import { Message } from '../components/Message';
import type { AccountPageState } from './AccountPage';

/** OP-1 利用者登録 */
export function RegisterPage() {
  const navigate = useNavigate();
  const { markAuthenticated } = useAuth();
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorItem[]>([]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // パスワードの入力欄は、送信したら成功しても失敗しても空にする (NFR-3)。
    // ほかの入力欄は、異常のときも入力し直さずに済むように値を残す
    const input = { email, displayName, password };
    setPassword('');
    setSubmitting(true);
    setErrorMessage(null);
    setFieldErrors([]);

    try {
      const { message, account } = await register(input);
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
      <h1>利用者登録</h1>
      <Message type="error" text={errorMessage} />
      <form noValidate onSubmit={handleSubmit}>
        <div>
          <label htmlFor="register-email">メールアドレス</label>
          <input
            id="register-email"
            name="email"
            type="text"
            inputMode="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            aria-describedby="register-email-error"
          />
          <FieldError field="email" errors={fieldErrors} id="register-email-error" />
        </div>
        <div>
          <label htmlFor="register-display-name">表示名</label>
          <input
            id="register-display-name"
            name="displayName"
            type="text"
            autoComplete="nickname"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            aria-describedby="register-display-name-error"
          />
          <FieldError field="displayName" errors={fieldErrors} id="register-display-name-error" />
        </div>
        <div>
          <label htmlFor="register-password">パスワード</label>
          <input
            id="register-password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-describedby="register-password-error"
          />
          <FieldError field="password" errors={fieldErrors} id="register-password-error" />
        </div>
        <button type="submit" disabled={submitting}>
          登録する
        </button>
      </form>
      <p>
        アカウントをお持ちの場合は <Link to="/login">ログイン</Link>
      </p>
    </main>
  );
}
