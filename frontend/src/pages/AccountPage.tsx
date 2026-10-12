import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { getMe, updateProfile } from '../api/auth';
import { ApiError } from '../api/client';
import type { Account, FieldErrorItem } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { FieldError } from '../components/FieldError';
import { LogoutButton } from '../components/LogoutButton';
import { Message } from '../components/Message';

function errorMessageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : String(error);
}

/** OP-4 と OP-5 自分のアカウントの情報の表示と変更 */
export function AccountPage() {
  const { markAuthenticated } = useAuth();
  // OP-4 で読んだ、今の表示名とメールアドレス。読み終わるまでは null
  const [current, setCurrent] = useState<Account | null>(null);
  const [loadErrorMessage, setLoadErrorMessage] = useState<string | null>(null);
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorItem[]>([]);

  // OP-4: 今の値を読み、表示と入力欄の初期値にする。
  // E-06 は api/client.ts と AuthContext が /login へ移すため、ここでは扱わない
  useEffect(() => {
    let active = true;
    getMe()
      .then(({ account }) => {
        if (!active) {
          return;
        }
        setCurrent(account);
        setDisplayName(account.displayName);
        setEmail(account.email);
        markAuthenticated(account);
      })
      .catch((error: unknown) => {
        if (active && !(error instanceof ApiError && error.code === 'E-06')) {
          setLoadErrorMessage(errorMessageOf(error));
        }
      });
    return () => {
      active = false;
    };
  }, [markAuthenticated]);

  // OP-5: 異常のときは入力欄の値を残す
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setSuccessMessage(null);
    setErrorMessage(null);
    setFieldErrors([]);
    try {
      const { message, account } = await updateProfile({ email, displayName });
      setCurrent(account);
      setDisplayName(account.displayName);
      setEmail(account.email);
      markAuthenticated(account);
      setSuccessMessage(message);
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
      setFieldErrors(error instanceof ApiError ? error.fieldErrors : []);
    } finally {
      setSubmitting(false);
    }
  }

  const hasError = (field: string) => fieldErrors.some((error) => error.field === field);

  return (
    <main>
      <h1>アカウントの情報</h1>
      <Message type="error" text={loadErrorMessage} />
      {current && (
        <>
          <section>
            <h2>今の情報</h2>
            <dl>
              <dt>表示名</dt>
              <dd data-testid="current-display-name">{current.displayName}</dd>
              <dt>メールアドレス</dt>
              <dd data-testid="current-email">{current.email}</dd>
            </dl>
          </section>
          <section>
            <h2>表示名とメールアドレスの変更</h2>
            <Message type="success" text={successMessage} />
            <Message type="error" text={errorMessage} />
            <form noValidate onSubmit={handleSubmit}>
              <div>
                <label htmlFor="displayName">表示名</label>
                <input
                  id="displayName"
                  name="displayName"
                  type="text"
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                  aria-invalid={hasError('displayName') || undefined}
                  aria-describedby={hasError('displayName') ? 'displayName-error' : undefined}
                />
                <FieldError field="displayName" errors={fieldErrors} id="displayName-error" />
              </div>
              <div>
                <label htmlFor="email">メールアドレス</label>
                <input
                  id="email"
                  name="email"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  aria-invalid={hasError('email') || undefined}
                  aria-describedby={hasError('email') ? 'email-error' : undefined}
                />
                <FieldError field="email" errors={fieldErrors} id="email-error" />
              </div>
              <button type="submit" disabled={submitting}>
                変更する
              </button>
            </form>
          </section>
        </>
      )}
      <nav>
        <ul>
          <li>
            <Link to="/account/password">パスワードの変更</Link>
          </li>
          <li>
            <Link to="/account/delete">アカウントの削除</Link>
          </li>
        </ul>
      </nav>
      <LogoutButton />
    </main>
  );
}
