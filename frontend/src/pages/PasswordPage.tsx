import { useState, type FormEvent } from 'react';
import { changePassword } from '../api/auth';
import { ApiError } from '../api/client';
import type { FieldErrorItem } from '../api/types';
import { FieldError } from '../components/FieldError';
import { Message } from '../components/Message';

/** OP-6 パスワードの変更 */
export function PasswordPage() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorItem[]>([]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const input = { currentPassword, newPassword };
    // パスワードの入力欄は、送信の後は成功しても失敗しても空にする (NFR-3)
    setCurrentPassword('');
    setNewPassword('');
    try {
      const { message } = await changePassword(input);
      setSuccessMessage(message);
      setErrorMessage(null);
      setFieldErrors([]);
    } catch (error) {
      // E-06 は AuthContext が未ログインにして /login へ移る
      setSuccessMessage(null);
      setErrorMessage(error instanceof ApiError ? error.message : String(error));
      setFieldErrors(error instanceof ApiError ? error.fieldErrors : []);
    }
  }

  return (
    <main>
      <h1>パスワードの変更</h1>
      <Message type="success" text={successMessage} />
      <Message type="error" text={errorMessage} />
      <form noValidate onSubmit={handleSubmit}>
        <div>
          <label htmlFor="currentPassword">今のパスワード</label>
          <input
            id="currentPassword"
            name="currentPassword"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            aria-describedby="currentPassword-error"
          />
          <FieldError field="currentPassword" errors={fieldErrors} id="currentPassword-error" />
        </div>
        <div>
          <label htmlFor="newPassword">新しいパスワード</label>
          <input
            id="newPassword"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            aria-describedby="newPassword-error"
          />
          <FieldError field="newPassword" errors={fieldErrors} id="newPassword-error" />
        </div>
        <button type="submit">変更する</button>
      </form>
    </main>
  );
}
