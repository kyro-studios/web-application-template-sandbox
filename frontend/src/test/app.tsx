import { render } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { vi } from 'vitest';
import { App } from '../App';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import { jsonResponse } from './http';

export const ACCOUNT = { email: 'User@Example.com', displayName: '山田 太郎' };

/** fetch を差し替える。GET /api/me はログイン中の応答を、ほかの要求は handler の結果を返す */
export function stubFetch(handler: (url: string, init?: RequestInit) => Promise<Response>) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url === '/api/me' && (init?.method ?? 'GET') === 'GET') {
      return Promise.resolve(jsonResponse(200, { account: ACCOUNT }));
    }
    return handler(url, init);
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

/** ルートとガードを含めたアプリケーションを、path を開いた状態で表示する */
export function renderApp(path: string) {
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
