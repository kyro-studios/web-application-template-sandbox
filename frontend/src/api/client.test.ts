import { afterEach, describe, expect, it, vi } from 'vitest';
import { errorResponse, jsonResponse, unauthorizedResponse } from '../test/http';
import { ApiError, request, setUnauthorizedHandler } from './client';

function mockFetch(response: Response | Promise<Response>) {
  const fetchMock = vi.fn<typeof fetch>(() => Promise.resolve(response));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function catchError(promise: Promise<unknown>): Promise<unknown> {
  try {
    await promise;
  } catch (error) {
    return error;
  }
  throw new Error('例外が起きませんでした');
}

describe('request', () => {
  let removeHandler: (() => void) | undefined;

  afterEach(() => {
    removeHandler?.();
    removeHandler = undefined;
  });

  it('本文を JSON で送り、成功の応答の本文を返す', async () => {
    const fetchMock = mockFetch(jsonResponse(200, { message: 'ログインしました' }));

    const result = await request('POST', '/api/session', { email: 'user@example.com', password: 'p' });

    expect(result).toEqual({ message: 'ログインしました' });
    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe('/api/session');
    expect(init?.method).toBe('POST');
    expect(init?.headers).toMatchObject({ 'Content-Type': 'application/json' });
    expect(init?.body).toBe(JSON.stringify({ email: 'user@example.com', password: 'p' }));
  });

  it('本文の無い要求には Content-Type を付けない', async () => {
    const fetchMock = mockFetch(jsonResponse(200, { message: 'ログアウトしました' }));

    await request('DELETE', '/api/session');

    const [, init] = fetchMock.mock.calls[0];
    expect(init?.body).toBeUndefined();
    expect(init?.headers).not.toHaveProperty('Content-Type');
  });

  it('エラーの応答を ApiError に変える', async () => {
    mockFetch(
      jsonResponse(400, {
        error: {
          code: 'E-01',
          message: '入力に誤りがあります',
          fieldErrors: [
            { field: 'email', message: 'メールアドレスの形式で入力してください' },
            { field: 'password', message: 'パスワードは 12 文字以上で入力してください' },
          ],
        },
      }),
    );

    const error = await catchError(request('POST', '/api/accounts', {}));

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.code).toBe('E-01');
    expect(apiError.message).toBe('入力に誤りがあります');
    expect(apiError.status).toBe(400);
    expect(apiError.fieldErrors).toEqual([
      { field: 'email', message: 'メールアドレスの形式で入力してください' },
      { field: 'password', message: 'パスワードは 12 文字以上で入力してください' },
    ]);
  });

  it('fieldErrors の無いエラーの応答では、fieldErrors を空にする', async () => {
    mockFetch(errorResponse(401, 'E-04', 'メールアドレスまたはパスワードが正しくありません'));

    const error = (await catchError(request('POST', '/api/session', {}))) as ApiError;

    expect(error.code).toBe('E-04');
    expect(error.message).toBe('メールアドレスまたはパスワードが正しくありません');
    expect(error.fieldErrors).toEqual([]);
  });

  it('エラーの応答の形でない場合は E-90 の ApiError にする', async () => {
    mockFetch(new Response('Bad Gateway', { status: 502 }));

    const error = (await catchError(request('GET', '/api/me'))) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('E-90');
    expect(error.status).toBe(502);
  });

  it('応答を受け取れなかった場合は E-90 の ApiError にする', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => Promise.reject(new TypeError('Failed to fetch'))));

    const error = (await catchError(request('GET', '/api/me'))) as ApiError;

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('E-90');
  });

  it('E-06 を受け取ったら、登録した処理を呼ぶ', async () => {
    const handler = vi.fn();
    removeHandler = setUnauthorizedHandler(handler);
    mockFetch(unauthorizedResponse());

    const error = await catchError(request('PUT', '/api/me/profile', {}));

    expect(handler).toHaveBeenCalledTimes(1);
    expect(handler).toHaveBeenCalledWith(error);
    expect((error as ApiError).code).toBe('E-06');
  });

  it('E-06 以外では、登録した処理を呼ばない', async () => {
    const handler = vi.fn();
    removeHandler = setUnauthorizedHandler(handler);
    mockFetch(errorResponse(401, 'E-04', 'メールアドレスまたはパスワードが正しくありません'));

    await catchError(request('POST', '/api/session', {}));

    expect(handler).not.toHaveBeenCalled();
  });

  it('handleUnauthorized が false なら、E-06 でも登録した処理を呼ばない', async () => {
    const handler = vi.fn();
    removeHandler = setUnauthorizedHandler(handler);
    mockFetch(unauthorizedResponse());

    const error = (await catchError(request('GET', '/api/me', undefined, { handleUnauthorized: false }))) as ApiError;

    expect(error.code).toBe('E-06');
    expect(handler).not.toHaveBeenCalled();
  });
});
