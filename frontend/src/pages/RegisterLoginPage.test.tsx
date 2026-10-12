import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../App';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import { E06_MESSAGE, errorResponse, jsonResponse, unauthorizedResponse } from '../test/http';

const E01_MESSAGE = '入力に誤りがあります';

const ACCOUNT = { email: 'User@Example.com', displayName: '山田 太郎' };

interface Handlers {
  /** GET /api/me の 2 回目以降の応答。1 回目 (起動時の確認) は常に E-06 (未ログイン) */
  me?: () => Promise<Response>;
  register?: () => Promise<Response>;
  login?: () => Promise<Response>;
}

/** fetch を差し替える。起動時の GET /api/me は E-06 を返し、未ログインにする */
function stubFetch(handlers: Handlers) {
  let meCount = 0;
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`;
    if (key === 'GET /api/me') {
      if (meCount++ === 0 || !handlers.me) {
        return Promise.resolve(unauthorizedResponse());
      }
      return handlers.me();
    }
    if (key === 'POST /api/accounts' && handlers.register) {
      return handlers.register();
    }
    if (key === 'POST /api/session' && handlers.login) {
      return handlers.login();
    }
    return Promise.reject(new Error(`想定外の要求: ${key}`));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

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

function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
        <LocationProbe />
        <AuthProbe />
      </AuthProvider>
    </MemoryRouter>,
  );
}

/** 未ログインで /login を開く */
async function openLoginPage() {
  renderApp('/login');
  await screen.findByRole('heading', { name: 'ログイン' });
}

/** 未ログインで /register を開く。起動時の確認の E-06 で /login へ移るため、ログインの画面のリンクから移る */
async function openRegisterPage() {
  await openLoginPage();
  fireEvent.click(screen.getByRole('link', { name: '利用者登録' }));
  await screen.findByRole('heading', { name: '利用者登録' });
}

function input(label: string): HTMLInputElement {
  return screen.getByLabelText(label) as HTMLInputElement;
}

function type(label: string, value: string) {
  fireEvent.change(input(label), { target: { value } });
}

async function submit(name: string) {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name }));
  });
}

function requestBody(fetchMock: ReturnType<typeof stubFetch>, method: string, url: string): unknown {
  const call = fetchMock.mock.calls.find(([u, init]) => String(u) === url && init?.method === method);
  expect(call).toBeDefined();
  return JSON.parse(call![1]!.body as string);
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

/** 入力の検査を行わない作りになっていることを確かめる */
function expectNoValidation(labels: string[]) {
  const form = screen.getByRole('button').closest('form');
  expect(form).toHaveAttribute('novalidate');
  for (const label of labels) {
    const element = input(label);
    expect(element).not.toHaveAttribute('required');
    expect(element).not.toHaveAttribute('maxlength');
    expect(element).not.toHaveAttribute('minlength');
    expect(element).not.toHaveAttribute('pattern');
  }
}

describe('RegisterPage (OP-1 利用者登録)', () => {
  it('メールアドレス、表示名、パスワードの入力欄と、ログインの画面へのリンクを表示する', async () => {
    stubFetch({});
    await openRegisterPage();

    expect(screen.getByTestId('location')).toHaveTextContent('/register');
    expect(input('メールアドレス')).toHaveAttribute('type', 'text');
    expect(input('表示名')).toHaveAttribute('type', 'text');
    expect(input('パスワード')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: '登録する' })).toBeInTheDocument();
    expectNoValidation(['メールアドレス', '表示名', 'パスワード']);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'ログイン' }));

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });

  it('入力の検査をせずに、入力のとおりの値で register を呼ぶ', async () => {
    const fetchMock = stubFetch({
      register: () => Promise.resolve(errorResponse(400, 'E-01', E01_MESSAGE, [])),
    });
    await openRegisterPage();

    type('メールアドレス', ' not an email ');
    type('表示名', '  ');
    type('パスワード', ' short ');
    await submit('登録する');

    expect(requestBody(fetchMock, 'POST', '/api/accounts')).toEqual({
      email: ' not an email ',
      displayName: '  ',
      password: ' short ',
    });
  });

  it('E-01 の fieldErrors を入力ごとに、message を画面に表示する', async () => {
    const fieldErrors = [
      { field: 'email', message: 'メールアドレスの形式で入力してください' },
      { field: 'displayName', message: '表示名を入力してください' },
      { field: 'password', message: 'パスワードは 12 文字以上で入力してください' },
    ];
    stubFetch({ register: () => Promise.resolve(errorResponse(400, 'E-01', E01_MESSAGE, fieldErrors)) });
    await openRegisterPage();

    type('メールアドレス', 'user');
    await submit('登録する');

    expect(await screen.findByRole('alert')).toHaveTextContent(E01_MESSAGE);
    expect(input('メールアドレス')).toHaveAccessibleDescription('メールアドレスの形式で入力してください');
    expect(input('表示名')).toHaveAccessibleDescription('表示名を入力してください');
    expect(input('パスワード')).toHaveAccessibleDescription('パスワードは 12 文字以上で入力してください');
    expect(screen.getByTestId('location')).toHaveTextContent('/register');
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
  });

  it.each([
    ['E-03', 409, 'このメールアドレスは使えません'],
    ['E-07', 409, '既にログインしています'],
    ['E-90', 503, '処理できませんでした。時間をおいてやり直してください'],
  ] as const)('%s の message をそのまま表示する', async (code, status, message) => {
    stubFetch({ register: () => Promise.resolve(errorResponse(status, code, message)) });
    await openRegisterPage();

    type('メールアドレス', ACCOUNT.email);
    await submit('登録する');

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByTestId('location')).toHaveTextContent('/register');
  });

  it('異常のときは、パスワードの入力欄を空にし、ほかの入力欄の値を残す', async () => {
    stubFetch({
      register: () => Promise.resolve(errorResponse(409, 'E-03', 'このメールアドレスは使えません')),
    });
    await openRegisterPage();

    type('メールアドレス', ACCOUNT.email);
    type('表示名', ACCOUNT.displayName);
    type('パスワード', 'correct horse battery');
    await submit('登録する');

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(input('パスワード')).toHaveValue('');
    expect(input('メールアドレス')).toHaveValue(ACCOUNT.email);
    expect(input('表示名')).toHaveValue(ACCOUNT.displayName);
  });

  it('送信したら、応答を待つ間もパスワードの入力欄を空にする', async () => {
    const response = deferred<Response>();
    stubFetch({ register: () => response.promise });
    await openRegisterPage();

    type('メールアドレス', ACCOUNT.email);
    type('表示名', ACCOUNT.displayName);
    type('パスワード', 'correct horse battery');
    await submit('登録する');

    expect(input('パスワード')).toHaveValue('');
    expect(input('メールアドレス')).toHaveValue(ACCOUNT.email);
    expect(screen.getByRole('button', { name: '登録する' })).toBeDisabled();

    await act(async () => {
      response.resolve(errorResponse(503, 'E-90', '処理できませんでした。時間をおいてやり直してください'));
    });

    expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled();
  });

  it('成功したら、ログイン中にして /account へ移り、message を表示する', async () => {
    const fetchMock = stubFetch({
      register: () =>
        Promise.resolve(jsonResponse(201, { message: 'アカウントを作成し、ログインしました', account: ACCOUNT })),
    });
    await openRegisterPage();

    type('メールアドレス', ACCOUNT.email);
    type('表示名', ` ${ACCOUNT.displayName} `);
    type('パスワード', 'correct horse battery');
    await submit('登録する');

    expect(await screen.findByRole('heading', { name: 'アカウントの情報' })).toBeInTheDocument();
    expect(screen.getByTestId('location').textContent).toBe('/account');
    expect(screen.getByTestId('auth')).toHaveTextContent('authenticated 山田 太郎 User@Example.com');
    expect(screen.getByRole('status')).toHaveTextContent('アカウントを作成し、ログインしました');
    expect(requestBody(fetchMock, 'POST', '/api/accounts')).toEqual({
      email: ACCOUNT.email,
      displayName: ` ${ACCOUNT.displayName} `,
      password: 'correct horse battery',
    });
  });
});

describe('LoginPage (OP-2 ログイン)', () => {
  it('メールアドレスとパスワードの入力欄と、利用者登録の画面へのリンクを表示する', async () => {
    stubFetch({});
    await openLoginPage();

    expect(input('メールアドレス')).toHaveAttribute('type', 'text');
    expect(input('パスワード')).toHaveAttribute('type', 'password');
    expect(screen.getByRole('button', { name: 'ログインする' })).toBeInTheDocument();
    expectNoValidation(['メールアドレス', 'パスワード']);

    fireEvent.click(screen.getByRole('link', { name: '利用者登録' }));

    expect(await screen.findByRole('heading', { name: '利用者登録' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/register');
  });

  it('E-06 で移ってきた場合は、E-06 のメッセージを表示する', async () => {
    stubFetch({});
    renderApp('/account');

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });

  it('リンクから移ってきた場合は、メッセージを表示しない', async () => {
    stubFetch({});
    await openRegisterPage();

    fireEvent.click(screen.getByRole('link', { name: 'ログイン' }));

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('入力の検査をせずに、入力のとおりの値で login を呼ぶ', async () => {
    const fetchMock = stubFetch({
      login: () => Promise.resolve(errorResponse(401, 'E-04', 'メールアドレスまたはパスワードが正しくありません')),
    });
    await openLoginPage();

    type('メールアドレス', ' user ');
    type('パスワード', ' ');
    await submit('ログインする');

    expect(requestBody(fetchMock, 'POST', '/api/session')).toEqual({ email: ' user ', password: ' ' });
  });

  it('E-01 の fieldErrors を入力ごとに、message を画面に表示する', async () => {
    const fieldErrors = [
      { field: 'email', message: 'メールアドレスを入力してください' },
      { field: 'password', message: 'パスワードを入力してください' },
    ];
    stubFetch({ login: () => Promise.resolve(errorResponse(400, 'E-01', E01_MESSAGE, fieldErrors)) });
    await openLoginPage();

    await submit('ログインする');

    await screen.findByText('メールアドレスを入力してください');
    expect(screen.getByRole('alert')).toHaveTextContent(E01_MESSAGE);
    expect(screen.getByRole('alert')).not.toHaveTextContent(E06_MESSAGE);
    expect(input('メールアドレス')).toHaveAccessibleDescription('メールアドレスを入力してください');
    expect(input('パスワード')).toHaveAccessibleDescription('パスワードを入力してください');
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });

  it.each([
    ['E-04', 401, 'メールアドレスまたはパスワードが正しくありません'],
    ['E-05', 429, '続けてログインに失敗したため、しばらくログインできません'],
    ['E-07', 409, '既にログインしています'],
    ['E-90', 503, '処理できませんでした。時間をおいてやり直してください'],
  ] as const)('%s の message をそのまま表示する', async (code, status, message) => {
    stubFetch({ login: () => Promise.resolve(errorResponse(status, code, message)) });
    await openLoginPage();

    type('メールアドレス', ACCOUNT.email);
    type('パスワード', 'wrong password');
    await submit('ログインする');

    await screen.findByText(message);
    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
  });

  it('異常のときは、パスワードの入力欄を空にし、メールアドレスの値を残す', async () => {
    stubFetch({
      login: () => Promise.resolve(errorResponse(401, 'E-04', 'メールアドレスまたはパスワードが正しくありません')),
    });
    await openLoginPage();

    type('メールアドレス', ACCOUNT.email);
    type('パスワード', 'wrong password');
    await submit('ログインする');

    await screen.findByText('メールアドレスまたはパスワードが正しくありません');
    expect(input('パスワード')).toHaveValue('');
    expect(input('メールアドレス')).toHaveValue(ACCOUNT.email);
  });

  it('送信したら、応答を待つ間もパスワードの入力欄を空にする', async () => {
    const response = deferred<Response>();
    stubFetch({ login: () => response.promise });
    await openLoginPage();

    type('メールアドレス', ACCOUNT.email);
    type('パスワード', 'correct horse battery');
    await submit('ログインする');

    expect(input('パスワード')).toHaveValue('');
    expect(input('メールアドレス')).toHaveValue(ACCOUNT.email);
    expect(screen.getByRole('button', { name: 'ログインする' })).toBeDisabled();

    await act(async () => {
      response.resolve(errorResponse(401, 'E-04', 'メールアドレスまたはパスワードが正しくありません'));
    });

    expect(screen.getByRole('button', { name: 'ログインする' })).toBeEnabled();
  });

  it('成功したら、ログイン中にして /account へ移る', async () => {
    const fetchMock = stubFetch({
      login: () => Promise.resolve(jsonResponse(200, { message: 'ログインしました', account: { displayName: '山田 太郎' } })),
      me: () => Promise.resolve(jsonResponse(200, { account: ACCOUNT })),
    });
    await openLoginPage();

    type('メールアドレス', 'user@example.com');
    type('パスワード', 'correct horse battery');
    await submit('ログインする');

    expect(await screen.findByRole('heading', { name: 'アカウントの情報' })).toBeInTheDocument();
    expect(screen.getByTestId('location').textContent).toBe('/account');
    expect(screen.getByTestId('auth')).toHaveTextContent('authenticated 山田 太郎 User@Example.com');
    expect(screen.getByRole('status')).toHaveTextContent('ログインしました');
    expect(requestBody(fetchMock, 'POST', '/api/session')).toEqual({
      email: 'user@example.com',
      password: 'correct horse battery',
    });
  });
});
