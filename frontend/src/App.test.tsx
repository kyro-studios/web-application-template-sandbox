import { act, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { changePassword, getMe } from './api/auth';
import { App } from './App';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { E06_MESSAGE, errorResponse, jsonResponse, unauthorizedResponse } from './test/http';

const E90_MESSAGE = '処理できませんでした。時間をおいてやり直してください';

const ACCOUNT = { email: 'User@Example.com', displayName: '山田 太郎' };

const HEADINGS = {
  register: '利用者登録',
  login: 'ログイン',
  account: 'アカウントの情報',
  password: 'パスワードの変更',
  delete: 'アカウントの削除',
};

function meOk(): Response {
  return jsonResponse(200, { account: ACCOUNT });
}

/** fetch を差し替える。GET /api/me は meResponse を、ほかは otherResponse を返す */
function stubFetch(meResponse: () => Promise<Response>, otherResponse?: () => Promise<Response>) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url === '/api/me' && (init?.method ?? 'GET') === 'GET') {
      return meResponse();
    }
    if (otherResponse) {
      return otherResponse();
    }
    return Promise.reject(new Error(`想定外の要求: ${init?.method} ${url}`));
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

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

function expectNoPage() {
  for (const heading of Object.values(HEADINGS)) {
    expect(screen.queryByRole('heading', { name: heading })).not.toBeInTheDocument();
  }
}

describe('起動時のログイン状態の確認', () => {
  it('GET /api/me が 200 ならログイン中になり、表示名とメールアドレスを持つ', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(meOk()));

    renderApp('/account');

    expect(await screen.findByRole('heading', { name: HEADINGS.account })).toBeInTheDocument();
    expect(screen.getByTestId('auth')).toHaveTextContent('authenticated 山田 太郎 User@Example.com');
    expect(fetchMock).toHaveBeenCalledWith('/api/me', expect.objectContaining({ method: 'GET' }));
  });

  it.each(['/login', '/register'])(
    'GET /api/me が E-06 なら、%s を開いても未ログインになり /login へ移り、E-06 のメッセージを表示する',
    async (path) => {
      stubFetch(() => Promise.resolve(unauthorizedResponse()));

      renderApp(path);

      expect(await screen.findByRole('heading', { name: HEADINGS.login })).toBeInTheDocument();
      expect(screen.getByTestId('location')).toHaveTextContent('/login');
      expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
      expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
    },
  );

  it.each([
    ['/account', '200', meOk, HEADINGS.account, '/account'],
    ['/account/password', '200', meOk, HEADINGS.password, '/account/password'],
    ['/account/delete', '200', meOk, HEADINGS.delete, '/account/delete'],
    ['/login', 'E-06', unauthorizedResponse, HEADINGS.login, '/login'],
    ['/register', 'E-06', unauthorizedResponse, HEADINGS.login, '/login'],
  ])('%s: 結果 (%s) が出るまでガードは画面を出さない', async (path, _label, response, heading, resultPath) => {
    const me = deferred<Response>();
    stubFetch(() => me.promise);

    renderApp(path);

    expect(screen.getByTestId('auth')).toHaveTextContent('checking');
    expectNoPage();
    expect(screen.getByTestId('location')).toHaveTextContent(path);

    await act(async () => {
      me.resolve(response());
    });

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    expect(screen.getByTestId('location').textContent).toBe(resultPath);
  });

  it('`/` は結果が出るまで移らない', async () => {
    const me = deferred<Response>();
    stubFetch(() => me.promise);

    renderApp('/');

    expectNoPage();
    expect(screen.getByTestId('location')).toHaveTextContent('/');

    await act(async () => {
      me.resolve(meOk());
    });

    expect(await screen.findByRole('heading', { name: HEADINGS.account })).toBeInTheDocument();
  });
});

describe('ガードとルート', () => {
  it.each(['/account', '/account/password', '/account/delete'])(
    'RequireAuth: 未ログインで %s を開くと /login へ移る',
    async (path) => {
      stubFetch(() => Promise.resolve(unauthorizedResponse()));

      renderApp(path);

      expect(await screen.findByRole('heading', { name: HEADINGS.login })).toBeInTheDocument();
      expect(screen.getByTestId('location')).toHaveTextContent('/login');
    },
  );

  it.each(['/login', '/register'])('RequireGuest: ログイン中で %s を開くと /account へ移る', async (path) => {
    stubFetch(() => Promise.resolve(meOk()));

    renderApp(path);

    expect(await screen.findByRole('heading', { name: HEADINGS.account })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/account');
  });

  it.each(['/', '/unknown', '/account/unknown'])('ログイン中で %s を開くと /account へ移る', async (path) => {
    stubFetch(() => Promise.resolve(meOk()));

    renderApp(path);

    expect(await screen.findByRole('heading', { name: HEADINGS.account })).toBeInTheDocument();
    expect(screen.getByTestId('location').textContent).toBe('/account');
  });

  it.each(['/', '/unknown', '/account/unknown'])('未ログインで %s を開くと /login へ移る', async (path) => {
    stubFetch(() => Promise.resolve(unauthorizedResponse()));

    renderApp(path);

    expect(await screen.findByRole('heading', { name: HEADINGS.login })).toBeInTheDocument();
    expect(screen.getByTestId('location').textContent).toBe('/login');
  });

  it.each(['/', '/login', '/register', '/account'])(
    'GET /api/me が E-90 なら、%s でメッセージと再試行のボタンを出し、再試行で確かめ直す',
    async (path) => {
      let meCount = 0;
      const fetchMock = stubFetch(() =>
        Promise.resolve(meCount++ === 0 ? errorResponse(503, 'E-90', E90_MESSAGE) : meOk()),
      );

      renderApp(path);

      expect(await screen.findByRole('alert')).toHaveTextContent(E90_MESSAGE);
      expect(screen.getByTestId('auth')).toHaveTextContent('error');
      expect(screen.getByTestId('location')).toHaveTextContent(path);
      expectNoPage();

      await act(async () => {
        screen.getByRole('button', { name: '再試行' }).click();
      });

      expect(await screen.findByRole('heading', { name: HEADINGS.account })).toBeInTheDocument();
      expect(screen.getByTestId('auth')).toHaveTextContent('authenticated');
      expect(screen.queryByText(E90_MESSAGE)).not.toBeInTheDocument();
      // 起動時の確認、再試行の確認、/account の画面の OP-4
      expect(fetchMock).toHaveBeenCalledTimes(3);
    },
  );

  it('GET /api/me の通信が失敗したら、E-90 のメッセージと再試行のボタンを出す', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')));

    renderApp('/login');

    expect(await screen.findByRole('alert')).toHaveTextContent(E90_MESSAGE);
    expect(screen.getByRole('button', { name: '再試行' })).toBeInTheDocument();
    expectNoPage();
  });
});

describe('API の応答の E-06', () => {
  it('未ログインになり /login へ移り、E-06 のメッセージを表示する', async () => {
    stubFetch(
      () => Promise.resolve(meOk()),
      () => Promise.resolve(unauthorizedResponse()),
    );

    renderApp('/account/password');
    expect(await screen.findByRole('heading', { name: HEADINGS.password })).toBeInTheDocument();

    await act(async () => {
      await changePassword({ currentPassword: 'old password', newPassword: 'new password 123' }).catch(() => {});
    });

    expect(await screen.findByRole('heading', { name: HEADINGS.login })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });

  it('ログイン中の後の GET /api/me (OP-4) の E-06 でも、/login へ移りメッセージを表示する', async () => {
    let meCount = 0;
    stubFetch(() => Promise.resolve(meCount++ === 0 ? meOk() : unauthorizedResponse()));

    renderApp('/account');
    expect(await screen.findByRole('heading', { name: HEADINGS.account })).toBeInTheDocument();

    await act(async () => {
      await getMe().catch(() => {});
    });

    expect(await screen.findByRole('heading', { name: HEADINGS.login })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });
});
