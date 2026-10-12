import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { getMe, updateProfile } from './api/auth';
import { App } from './App';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { E06_MESSAGE, errorResponse, jsonResponse, unauthorizedResponse } from './test/http';

const ACCOUNT = { email: 'User@Example.com', displayName: '山田 太郎' };

const HEADINGS = {
  '/register': '利用者登録',
  '/login': 'ログイン',
  '/account': 'アカウントの情報',
  '/account/password': 'パスワードの変更',
  '/account/delete': 'アカウントの削除',
} as const;

function loggedIn(): Response {
  return jsonResponse(200, { account: ACCOUNT });
}

/** fetch を差し替える。応答は呼ばれた順に返す */
function mockFetch(...responses: Array<() => Response | Promise<Response>>) {
  const fetchMock = vi.fn<typeof fetch>(() => {
    const next = responses.shift();
    if (next === undefined) {
      return Promise.reject(new Error('想定していない API の呼び出しです'));
    }
    return Promise.resolve(next());
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

function AuthProbe() {
  const { status, account } = useAuth();
  return <output data-testid="auth">{status === 'authenticated' ? `${status}:${account?.email}` : status}</output>;
}

function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AuthProbe />
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function expectPage(heading: string) {
  expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
}

describe('起動時のログイン状態の確認', () => {
  it('起動時に GET /api/me を呼び、200 ならログイン中になる', async () => {
    const fetchMock = mockFetch(loggedIn);

    renderApp('/account');

    await expectPage(HEADINGS['/account']);
    expect(screen.getByTestId('auth')).toHaveTextContent(`authenticated:${ACCOUNT.email}`);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe('/api/me');
    expect(init?.method).toBe('GET');
  });

  it('E-06 なら未ログインになり、メッセージは表示しない', async () => {
    mockFetch(unauthorizedResponse);

    renderApp('/login');

    await expectPage(HEADINGS['/login']);
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('E-06 以外の異常なら未ログインになり、応答のメッセージを表示する', async () => {
    mockFetch(() => errorResponse(503, 'E-90', '処理できませんでした。時間をおいてやり直してください'));

    renderApp('/login');

    await expectPage(HEADINGS['/login']);
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.getByRole('alert')).toHaveTextContent('処理できませんでした。時間をおいてやり直してください');
  });

  it.each(Object.entries(HEADINGS))('結果が出るまで、%s の画面を出さない (ログイン中になる場合)', async (path, heading) => {
    const response = deferred<Response>();
    mockFetch(() => response.promise);

    renderApp(path);

    expect(screen.getByTestId('auth')).toHaveTextContent('checking');
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();

    await act(async () => {
      response.resolve(loggedIn());
    });

    const expected = path === '/register' || path === '/login' ? HEADINGS['/account'] : heading;
    await expectPage(expected);
  });

  it.each(Object.entries(HEADINGS))('結果が出るまで、%s の画面を出さない (未ログインになる場合)', async (path, heading) => {
    const response = deferred<Response>();
    mockFetch(() => response.promise);

    renderApp(path);

    expect(screen.getByTestId('auth')).toHaveTextContent('checking');
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();

    await act(async () => {
      response.resolve(unauthorizedResponse());
    });

    const expected = path === '/register' || path === '/login' ? heading : HEADINGS['/login'];
    await expectPage(expected);
  });
});

describe('ガード', () => {
  it.each(['/account', '/account/password', '/account/delete'] as const)(
    'RequireAuth: 未ログインで %s を開くと /login へ移る',
    async (path) => {
      mockFetch(unauthorizedResponse);

      renderApp(path);

      await expectPage(HEADINGS['/login']);
    },
  );

  it.each(['/account', '/account/password', '/account/delete'] as const)(
    'RequireAuth: ログイン中なら %s を表示する',
    async (path) => {
      mockFetch(loggedIn);

      renderApp(path);

      await expectPage(HEADINGS[path]);
    },
  );

  it.each(['/register', '/login'] as const)('RequireGuest: ログイン中に %s を開くと /account へ移る', async (path) => {
    mockFetch(loggedIn);

    renderApp(path);

    await expectPage(HEADINGS['/account']);
  });

  it.each(['/register', '/login'] as const)('RequireGuest: 未ログインなら %s を表示する', async (path) => {
    mockFetch(unauthorizedResponse);

    renderApp(path);

    await expectPage(HEADINGS[path]);
  });

  it.each(['/', '/unknown', '/account/unknown'])('ログイン中に %s を開くと /account へ移る', async (path) => {
    mockFetch(loggedIn);

    renderApp(path);

    await expectPage(HEADINGS['/account']);
  });

  it.each(['/', '/unknown', '/account/unknown'])('未ログインで %s を開くと /login へ移る', async (path) => {
    mockFetch(unauthorizedResponse);

    renderApp(path);

    await expectPage(HEADINGS['/login']);
  });

  it('/ を開いても、結果が出るまで画面を出さない', async () => {
    const response = deferred<Response>();
    mockFetch(() => response.promise);

    renderApp('/');

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();

    await act(async () => {
      response.resolve(loggedIn());
    });

    await expectPage(HEADINGS['/account']);
  });
});

describe('API の応答の E-06', () => {
  it('どの API でも E-06 を受け取ったら、未ログインになり /login へ移り、E-06 のメッセージを表示する', async () => {
    mockFetch(loggedIn, unauthorizedResponse);
    renderApp('/account/password');
    await expectPage(HEADINGS['/account/password']);

    await act(async () => {
      await updateProfile({ email: 'new@example.com', displayName: '山田 花子' }).catch(() => undefined);
    });

    await expectPage(HEADINGS['/login']);
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });

  it('GET /api/me の E-06 でも同じく /login へ移り、メッセージを表示する', async () => {
    mockFetch(loggedIn, unauthorizedResponse);
    renderApp('/account');
    await expectPage(HEADINGS['/account']);

    await act(async () => {
      await getMe().catch(() => undefined);
    });

    await expectPage(HEADINGS['/login']);
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });
});
