import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { deleteAccount } from '../api/auth';
import { ApiError } from '../api/client';
import type { FieldErrorItem } from '../api/types';
import { useAuth, type LoginRedirectState } from '../auth/AuthContext';
import { FieldError } from '../components/FieldError';
import { Message } from '../components/Message';

/** OP-7 アカウントの削除 */
export function DeleteAccountPage() {
  const navigate = useNavigate();
  const { markUnauthenticated } = useAuth();
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorItem[]>([]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = { password };
    // パスワードの入力欄は、送信の後は成功しても失敗しても空にする (NFR-3)
    setPassword('');
    try {
      const { message } = await deleteAccount(input);
      // 削除と同時にログアウトしているため、未ログインにして /login で message を表示する。
      // 画面の遷移より先に RequireAuth が /login へ移すことがあるため、遷移の状態は AuthContext にも渡す
      const loginRedirectState: LoginRedirectState = { successMessage: message };
      markUnauthenticated(loginRedirectState);
      navigate('/login', { replace: true, state: loginRedirectState });
    } catch (error) {
      // E-06 は AuthContext が未ログインにして /login へ移る
      setErrorMessage(error instanceof ApiError ? error.message : String(error));
      setFieldErrors(error instanceof ApiError ? error.fieldErrors : []);
    }
  }

  return (
    <main>
      <h1>アカウントの削除</h1>
      <Message type="error" text={errorMessage} />
      <form noValidate onSubmit={handleSubmit}>
        <div>
          <label htmlFor="password">パスワード</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            aria-describedby="password-error"
          />
          <FieldError field="password" errors={fieldErrors} id="password-error" />
        </div>
        <button type="submit">削除する</button>
      </form>
    </main>
  );
}
