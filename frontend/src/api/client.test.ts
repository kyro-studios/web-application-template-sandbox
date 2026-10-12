import { afterEach, describe, expect, it, vi } from 'vitest';
import { errorResponse, jsonResponse, unauthorizedResponse } from '../test/http';
import { changePassword, getMe, logout, register } from './auth';
import { ApiError, request, setUnauthorizedHandler } from './client';

function stubFetch(response: Response | (() => Promise<Response>)) {
  const fetchMock = vi.fn(typeof response === 'function' ? response : () => Promise.resolve(response));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function catchApiError(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    expect(error).toBeInstanceOf(ApiError);
    return error as ApiError;
  }
  throw new Error('ApiError が投げられませんでした');
}

describe('request', () => {
  let removeHandler: (() => void) | undefined;

  afterEach(() => {
    removeHandler?.();
    removeHandler = undefined;
  });

  it('本文を JSON で送り、成功の応答の本文を返す', async () => {
    const body = { message: 'アカウントを作成し、ログインしました', account: { email: 'User@Example.com', displayName: '山田 太郎' } };
    const fetchMock = stubFetch(jsonResponse(201, body));

    const result = await register({ email: 'User@Example.com', displayName: '山田 太郎', password: 'correct horse battery' });

    expect(result).toEqual(body);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/accounts');
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body as string)).toEqual({
      email: 'User@Example.com',
      displayName: '山田 太郎',
      password: 'correct horse battery',
    });
  });

  it('本文が無い要求では Content-Type を付けない', async () => {
    const fetchMock = stubFetch(jsonResponse(200, { message: 'ログアウトしました' }));

    await logout();

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/session');
    expect(init.method).toBe('DELETE');
    expect(init.body).toBeUndefined();
    expect((init.headers as Record<string, string>)['Content-Type']).toBeUndefined();
  });

  it('エラーの応答を ApiError (code、message、fieldErrors) に変える', async () => {
    const fieldErrors = [
      { field: 'email', message: 'メールアドレスの形式で入力してください' },
      { field: 'password', message: 'パスワードは 12 文字以上で入力してください' },
    ];
    stubFetch(errorResponse(400, 'E-01', '入力に誤りがあります', fieldErrors));

    const error = await catchApiError(register({ email: 'x', displayName: 'y', password: 'z' }));

    expect(error.code).toBe('E-01');
    expect(error.message).toBe('入力に誤りがあります');
    expect(error.fieldErrors).toEqual(fieldErrors);
    expect(error.status).toBe(400);
  });

  it('fieldErrors の無いエラーの応答では、fieldErrors を空にする', async () => {
    stubFetch(errorResponse(409, 'E-03', 'このメールアドレスは使えません'));

    const error = await catchApiError(request('POST', '/api/accounts', {}));

    expect(error.code).toBe('E-03');
    expect(error.message).toBe('このメールアドレスは使えません');
    expect(error.fieldErrors).toEqual([]);
  });

  it('エラーの応答の形で無い応答は E-90 にする', async () => {
    stubFetch(new Response('<html>Bad Gateway</html>', { status: 502 }));

    const error = await catchApiError(request('GET', '/api/me'));

    expect(error.code).toBe('E-90');
    expect(error.status).toBe(502);
  });

  it('通信に失敗したら E-90 にする', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')));

    const error = await catchApiError(request('GET', '/api/me'));

    expect(error.code).toBe('E-90');
    expect(error.status).toBe(0);
  });

  it('E-06 を受け取ったら、登録した処理を呼んでから ApiError を投げる', async () => {
    const handler = vi.fn();
    removeHandler = setUnauthorizedHandler(handler);
    stubFetch(unauthorizedResponse());

    const error = await catchApiError(changePassword({ currentPassword: 'a', newPassword: 'b' }));

    expect(error.code).toBe('E-06');
    expect(handler).toHaveBeenCalledTimes(1);
    expect(handler).toHaveBeenCalledWith(error);
  });

  it('E-06 以外の異常では、登録した処理を呼ばない', async () => {
    const handler = vi.fn();
    removeHandler = setUnauthorizedHandler(handler);
    stubFetch(errorResponse(401, 'E-04', 'メールアドレスまたはパスワードが正しくありません'));

    await catchApiError(request('POST', '/api/session', {}));

    expect(handler).not.toHaveBeenCalled();
  });

  it('GET /api/me (起動時の確認を含む) の E-06 でも、登録した処理を呼ぶ', async () => {
    const handler = vi.fn();
    removeHandler = setUnauthorizedHandler(handler);
    stubFetch(unauthorizedResponse());

    const error = await catchApiError(getMe());

    expect(error.code).toBe('E-06');
    expect(handler).toHaveBeenCalledWith(error);
  });
});
