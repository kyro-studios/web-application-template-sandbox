import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import { E06_MESSAGE, errorResponse, jsonResponse, unauthorizedResponse } from '../test/http';

const E90_MESSAGE = '処理できませんでした。時間をおいてやり直してください';

const ACCOUNT = { email: 'User@Example.com', displayName: '山田 太郎' };

type Handler = () => Promise<Response>;

/** fetch を差し替える。「メソッド パス」ごとに、呼ばれた順に応答を返す (最後の応答は繰り返す) */
function stubFetch(routes: Record<string, Handler[]>) {
  const counts: Record<string, number> = {};
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`;
    const handlers = routes[key];
    if (!handlers) {
      return Promise.reject(new Error(`想定外の要求: ${key}`));
    }
    const index = Math.min(counts[key] ?? 0, handlers.length - 1);
    counts[key] = (counts[key] ?? 0) + 1;
    return handlers[index]();
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const ok = (status: number, body: unknown): Handler => () => Promise.resolve(jsonResponse(status, body));
const meOk = ok(200, { account: ACCOUNT });

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function AuthProbe() {
  const { status, account } = useAuth();
  return (
    <div data-testid="auth">
      {status}
      {account ? ` ${account.displayName} ${account.email}` : ''}
    </div>
  );
}

function renderAccount() {
  return render(
    <MemoryRouter initialEntries={['/account']}>
      <AuthProvider>
        <App />
        <LocationProbe />
        <AuthProbe />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function waitForForm() {
  await screen.findByRole('heading', { name: 'アカウントの情報' });
  await screen.findByLabelText('表示名');
}

function fillAndSubmit(displayName: string, email: string) {
  fireEvent.change(screen.getByLabelText('表示名'), { target: { value: displayName } });
  fireEvent.change(screen.getByLabelText('メールアドレス'), { target: { value: email } });
  fireEvent.click(screen.getByRole('button', { name: '変更する' }));
}

describe('AccountPage (OP-4)', () => {
  it('今の表示名とメールアドレスを表示し、入力欄の初期値にする', async () => {
    const fetchMock = stubFetch({ 'GET /api/me': [meOk] });

    renderAccount();
    await waitForForm();

    expect(screen.getByTestId('current-display-name')).toHaveTextContent('山田 太郎');
    expect(screen.getByTestId('current-email')).toHaveTextContent('User@Example.com');
    expect(screen.getByLabelText('表示名')).toHaveValue('山田 太郎');
    expect(screen.getByLabelText('メールアドレス')).toHaveValue('User@Example.com');
    // 起動時の確認と、この画面の OP-4
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('入力の検査は API に任せる (noValidate で、required や maxLength を付けない)', async () => {
    stubFetch({ 'GET /api/me': [meOk] });

    renderAccount();
    await waitForForm();

    const form = screen.getByRole('button', { name: '変更する' }).closest('form');
    expect(form).toHaveAttribute('novalidate');
    for (const label of ['表示名', 'メールアドレス']) {
      const input = screen.getByLabelText(label);
      expect(input).not.toBeRequired();
      expect(input).not.toHaveAttribute('maxlength');
    }
  });

  it('パスワードの変更と削除の画面へのリンクを置く', async () => {
    stubFetch({ 'GET /api/me': [meOk] });

    renderAccount();
    await waitForForm();

    expect(screen.getByRole('link', { name: 'パスワードの変更' })).toHaveAttribute('href', '/account/password');
    expect(screen.getByRole('link', { name: 'アカウントの削除' })).toHaveAttribute('href', '/account/delete');
  });

  it('ログアウトのボタンを置き、押すと /login へ移り、成功の message を表示する', async () => {
    stubFetch({ 'GET /api/me': [meOk], 'DELETE /api/session': [ok(200, { message: 'ログアウトしました' })] });

    renderAccount();
    await waitForForm();
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
    });

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.getByRole('status')).toHaveTextContent('ログアウトしました');
  });

  it('OP-4 が E-90 なら、メッセージを表示する', async () => {
    stubFetch({ 'GET /api/me': [meOk, () => Promise.resolve(errorResponse(503, 'E-90', E90_MESSAGE))] });

    renderAccount();

    expect(await screen.findByRole('alert')).toHaveTextContent(E90_MESSAGE);
    expect(screen.queryByLabelText('表示名')).not.toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/account');
  });

  it('OP-4 が E-06 なら、/login へ移りメッセージを表示する', async () => {
    stubFetch({ 'GET /api/me': [meOk, () => Promise.resolve(unauthorizedResponse())] });

    renderAccount();

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });
});

describe('AccountPage (OP-5)', () => {
  it('成功したら、表示と AuthContext を更新し、message を表示する', async () => {
    const updated = { email: 'new@example.com', displayName: '山田 花子' };
    const fetchMock = stubFetch({
      'GET /api/me': [meOk],
      'PUT /api/me/profile': [ok(200, { message: 'アカウントの情報を変更しました', account: updated })],
    });

    renderAccount();
    await waitForForm();
    fillAndSubmit('山田 花子', 'new@example.com');

    expect(await screen.findByRole('status')).toHaveTextContent('アカウントの情報を変更しました');
    expect(screen.getByTestId('current-display-name')).toHaveTextContent('山田 花子');
    expect(screen.getByTestId('current-email')).toHaveTextContent('new@example.com');
    expect(screen.getByLabelText('表示名')).toHaveValue('山田 花子');
    expect(screen.getByLabelText('メールアドレス')).toHaveValue('new@example.com');
    expect(screen.getByTestId('auth')).toHaveTextContent('authenticated 山田 花子 new@example.com');

    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/me/profile' && init?.method === 'PUT');
    expect(JSON.parse(call![1]!.body as string)).toEqual({ email: 'new@example.com', displayName: '山田 花子' });
  });

  it('E-01 なら、fieldErrors と message をそのまま表示し、入力欄の値を残す', async () => {
    const fieldErrors = [
      { field: 'email', message: 'メールアドレスの形式で入力してください' },
      { field: 'displayName', message: '表示名を入力してください' },
    ];
    stubFetch({
      'GET /api/me': [meOk],
      'PUT /api/me/profile': [() => Promise.resolve(errorResponse(400, 'E-01', '入力に誤りがあります', fieldErrors))],
    });

    renderAccount();
    await waitForForm();
    fillAndSubmit('', 'invalid');

    expect(await screen.findByRole('alert')).toHaveTextContent('入力に誤りがあります');
    expect(screen.getByText('メールアドレスの形式で入力してください')).toBeInTheDocument();
    expect(screen.getByText('表示名を入力してください')).toBeInTheDocument();
    expect(screen.getByLabelText('メールアドレス')).toHaveAccessibleDescription('メールアドレスの形式で入力してください');
    expect(screen.getByLabelText('表示名')).toHaveAccessibleDescription('表示名を入力してください');
    expect(screen.getByLabelText('表示名')).toHaveValue('');
    expect(screen.getByLabelText('メールアドレス')).toHaveValue('invalid');
    // 今の値の表示は変わらない
    expect(screen.getByTestId('current-display-name')).toHaveTextContent('山田 太郎');
    expect(screen.getByTestId('current-email')).toHaveTextContent('User@Example.com');
  });

  it('E-03 なら、message を表示し、入力欄の値を残す', async () => {
    stubFetch({
      'GET /api/me': [meOk],
      'PUT /api/me/profile': [() => Promise.resolve(errorResponse(409, 'E-03', 'このメールアドレスは使えません'))],
    });

    renderAccount();
    await waitForForm();
    fillAndSubmit('山田 花子', 'taken@example.com');

    expect(await screen.findByRole('alert')).toHaveTextContent('このメールアドレスは使えません');
    expect(screen.getByLabelText('表示名')).toHaveValue('山田 花子');
    expect(screen.getByLabelText('メールアドレス')).toHaveValue('taken@example.com');
    expect(screen.getByTestId('auth')).toHaveTextContent('authenticated 山田 太郎 User@Example.com');
  });

  it('前の送信のメッセージは、次の送信の結果で置き換える', async () => {
    stubFetch({
      'GET /api/me': [meOk],
      'PUT /api/me/profile': [
        () => Promise.resolve(errorResponse(409, 'E-03', 'このメールアドレスは使えません')),
        ok(200, { message: 'アカウントの情報を変更しました', account: ACCOUNT }),
      ],
    });

    renderAccount();
    await waitForForm();
    fillAndSubmit('山田 太郎', 'taken@example.com');
    expect(await screen.findByRole('alert')).toHaveTextContent('このメールアドレスは使えません');

    fillAndSubmit('山田 太郎', 'User@Example.com');

    expect(await screen.findByRole('status')).toHaveTextContent('アカウントの情報を変更しました');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('応答を待つ間にログアウトしたら、遅れて届いた成功の応答でログイン中に戻さない', async () => {
    const updated = { email: 'new@example.com', displayName: '山田 花子' };
    let resolveUpdate: (response: Response) => void = () => {};
    stubFetch({
      'GET /api/me': [meOk],
      'PUT /api/me/profile': [() => new Promise<Response>((resolve) => (resolveUpdate = resolve))],
      'DELETE /api/session': [ok(200, { message: 'ログアウトしました' })],
    });

    renderAccount();
    await waitForForm();
    await act(async () => {
      fillAndSubmit('山田 花子', 'new@example.com');
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
    });
    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();

    await act(async () => {
      resolveUpdate(jsonResponse(200, { message: 'アカウントの情報を変更しました', account: updated }));
    });

    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(screen.getByTestId('auth')).toHaveTextContent(/^unauthenticated$/);
    expect(screen.getByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('ログアウトしました');
  });

  it('E-06 なら、未ログインになり /login へ移り、E-06 のメッセージを表示する', async () => {
    stubFetch({
      'GET /api/me': [meOk],
      'PUT /api/me/profile': [() => Promise.resolve(unauthorizedResponse())],
    });

    renderAccount();
    await waitForForm();
    await act(async () => {
      fillAndSubmit('山田 花子', 'new@example.com');
    });

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });
});
