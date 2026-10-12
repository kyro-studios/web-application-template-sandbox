import { useLocation } from 'react-router';
import type { LoginRedirectState } from '../auth/AuthContext';
import { Message } from '../components/Message';

/** OP-2 ログイン (仮。中身は後のタスクで作る) */
export function LoginPage() {
  const location = useLocation();
  const redirectState = location.state as Partial<LoginRedirectState> | null;

  return (
    <main>
      <h1>ログイン</h1>
      <Message type="error" text={redirectState?.errorMessage} />
    </main>
  );
}
