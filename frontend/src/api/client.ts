import type { ApiFieldError, ErrorCode } from './types';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

/** API のエラーの応答 (5.2) */
export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly fieldErrors: ApiFieldError[];
  /** HTTP の状態。応答を受け取れなかった場合は 0 */
  readonly status: number;

  constructor(code: ErrorCode, message: string, fieldErrors: ApiFieldError[] = [], status = 0) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.status = status;
  }
}

export interface RequestOptions {
  /**
   * E-06 を受け取ったときに、setUnauthorizedHandler で登録した処理を呼ぶか。既定は呼ぶ。
   * 起動時のログイン状態の確認のように、E-06 を未ログインの知らせとして扱う場合だけ false にする
   */
  handleUnauthorized?: boolean;
}

// 応答を受け取れなかった、またはエラーの応答の形でなかった場合に使う。E-90 の内容 (5.2) と同じ
const UNAVAILABLE_MESSAGE = '処理できませんでした。時間をおいてやり直してください';

type UnauthorizedHandler = (error: ApiError) => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/** どの API でも E-06 を受け取ったときに呼ぶ処理を登録する。登録を外す関数を返す */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null;
    }
  };
}

export async function request<T>(
  method: HttpMethod,
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const init: RequestInit = { method, headers, credentials: 'same-origin' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(path, init);
  } catch {
    throw new ApiError('E-90', UNAVAILABLE_MESSAGE);
  }

  const payload = await readJson(response);
  if (response.ok) {
    return payload as T;
  }

  const error = toApiError(response.status, payload);
  if (error.code === 'E-06' && options.handleUnauthorized !== false) {
    unauthorizedHandler?.(error);
  }
  throw error;
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return undefined;
  }
}

function toApiError(status: number, payload: unknown): ApiError {
  const error = isRecord(payload) ? payload.error : undefined;
  if (!isRecord(error) || typeof error.code !== 'string' || typeof error.message !== 'string') {
    return new ApiError('E-90', UNAVAILABLE_MESSAGE, [], status);
  }
  const fieldErrors = Array.isArray(error.fieldErrors)
    ? error.fieldErrors.filter(isFieldError).map(({ field, message }) => ({ field, message }))
    : [];
  return new ApiError(error.code as ErrorCode, error.message, fieldErrors, status);
}

function isFieldError(value: unknown): value is ApiFieldError {
  return isRecord(value) && typeof value.field === 'string' && typeof value.message === 'string';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
