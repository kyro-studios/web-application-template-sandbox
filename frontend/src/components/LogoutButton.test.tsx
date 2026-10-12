import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import { errorResponse, jsonResponse } from '../test/http';
import { LogoutButton } from './LogoutButton';

const E90_MESSAGE = '処理できませんでした。時間をおいてやり直してください';

const ACCOUNT = { email: 'User@Example.com', displayName: '山田 太郎' };

/** fetch を差し替える。GET /api/me はログイン中を返し、DELETE /api/session は logoutResponse を返す */
function stubFetch(logoutResponse: () => Promise<Response>) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const key = `${init?.method ?? 'GET'} ${String(input)}`;
    if (key === 'GET /api/me') {
      return Promise.resolve(jsonResponse(200, { account: ACCOUNT }));
    }
    if (key === 'DELETE /api/session') {
      return logoutResponse();
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
  const { status } = useAuth();
  return <div data-testid="auth">{status}</div>;
}

function renderButton() {
  return render(
    <MemoryRouter initialEntries={['/account']}>
      <AuthProvider>
        <Routes>
          <Route path="/account" element={<LogoutButton />} />
          <Route path="/login" element={<h1>ログイン</h1>} />
        </Routes>
        <LocationProbe />
        <AuthProbe />
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function clickLogout() {
  await screen.findByText('authenticated');
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
  });
}

describe('LogoutButton (OP-3)', () => {
  it('成功したら、未ログインになり /login へ移る', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(jsonResponse(200, { message: 'ログアウトしました' })));

    renderButton();
    await clickLogout();

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/login');
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(fetchMock).toHaveBeenCalledWith('/api/session', expect.objectContaining({ method: 'DELETE' }));
  });

  it('E-90 なら、メッセージを表示し、ログイン中のまま残る', async () => {
    stubFetch(() => Promise.resolve(errorResponse(503, 'E-90', E90_MESSAGE)));

    renderButton();
    await clickLogout();

    expect(await screen.findByRole('alert')).toHaveTextContent(E90_MESSAGE);
    expect(screen.getByTestId('location')).toHaveTextContent('/account');
    expect(screen.getByTestId('auth')).toHaveTextContent('authenticated');
    expect(screen.getByRole('button', { name: 'ログアウト' })).toBeEnabled();
  });

  it('通信が失敗したら、E-90 のメッセージを表示し、やり直せる', async () => {
    let count = 0;
    stubFetch(() =>
      count++ === 0
        ? Promise.reject(new TypeError('Failed to fetch'))
        : Promise.resolve(jsonResponse(200, { message: 'ログアウトしました' })),
    );

    renderButton();
    await clickLogout();
    expect(await screen.findByRole('alert')).toHaveTextContent(E90_MESSAGE);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }));
    });

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
  });
});
