import { Message } from '../components/Message';
import { useAuth } from './AuthContext';

/** 起動時の GET /api/me が E-06 以外の異常で終わったときに、メッセージと確かめ直すボタンを出す */
export function AuthCheckError() {
  const { checkErrorMessage, retryCheck } = useAuth();

  return (
    <main>
      <Message type="error" text={checkErrorMessage} />
      <button type="button" onClick={retryCheck}>
        再試行
      </button>
    </main>
  );
}
