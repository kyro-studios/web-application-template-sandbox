import { useState } from 'react';
import { useNavigate } from 'react-router';
import { logout } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuth, type LoginRedirectState } from '../auth/AuthContext';
import { Message } from './Message';

/**
 * OP-3 ログアウト。成功したら未ログインにして /login へ移り、成功の message を表示する。
 * E-90 ではメッセージを表示し、ログイン中のまま残る
 */
export function LogoutButton() {
  const navigate = useNavigate();
  const { markUnauthenticated } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleClick() {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const { message } = await logout();
      const loginRedirectState: LoginRedirectState = { successMessage: message };
      markUnauthenticated(loginRedirectState);
      navigate('/login', { replace: true, state: loginRedirectState });
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : String(error));
      setSubmitting(false);
    }
  }

  return (
    <div>
      <Message type="error" text={errorMessage} />
      <button type="button" onClick={handleClick} disabled={submitting}>
        ログアウト
      </button>
    </div>
  );
}
