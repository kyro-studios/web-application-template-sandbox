import type { ErrorCode, ErrorResponse, FieldErrorItem } from './types';

const ERROR_CODES: readonly ErrorCode[] = [
  'E-01',
  'E-02',
  'E-03',
  'E-04',
  'E-05',
  'E-06',
  'E-07',
  'E-08',
  'E-90',
];

// エラーの応答の形で無い応答 (中継の失敗など) と、通信そのものの失敗は E-90 として扱う。
// バックエンドの応答が無いため、E-90 のメッセージ (アーキテクチャ設計 5.2) をここに持つ
const UNAVAILABLE_MESSAGE = '処理できませんでした。時間をおいてやり直してください';

/** API の異常の応答。code、message、fieldErrors はエラーの応答 (5.2) の値をそのまま持つ */
export class ApiError extends Error {
  readonly code: ErrorCode;
  readonly fieldErrors: FieldErrorItem[];
  /** HTTP の状態。通信そのものが失敗した場合は 0 */
  readonly status: number;

  constructor(code: ErrorCode, message: string, fieldErrors: FieldErrorItem[] = [], status = 0) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.status = status;
  }
}

type UnauthorizedHandler = (error: ApiError) => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/**
 * E-06 の応答を受け取ったときに呼ぶ処理を登録する (AuthContext が登録する)。
 * 戻り値の関数を呼ぶと、登録を外す
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null;
    }
  };
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

/** API を呼ぶ。成功なら応答の本文を返し、異常なら ApiError を投げる */
export async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError('E-90', UNAVAILABLE_MESSAGE);
  }

  const payload = await readJson(response);

  if (response.ok) {
    return payload as T;
  }

  const error = toApiError(payload, response.status);
  if (error.code === 'E-06') {
    unauthorizedHandler?.(error);
  }
  throw error;
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text().catch(() => '');
  if (text === '') {
    return undefined;
  }
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

function toApiError(payload: unknown, status: number): ApiError {
  if (!isErrorResponse(payload)) {
    return new ApiError('E-90', UNAVAILABLE_MESSAGE, [], status);
  }
  const { code, message, fieldErrors } = payload.error;
  return new ApiError(code, message, fieldErrors ?? [], status);
}

function isErrorResponse(payload: unknown): payload is ErrorResponse {
  if (typeof payload !== 'object' || payload === null) {
    return false;
  }
  const error = (payload as { error?: unknown }).error;
  if (typeof error !== 'object' || error === null) {
    return false;
  }
  const { code, message, fieldErrors } = error as Record<string, unknown>;
  return (
    typeof code === 'string' &&
    (ERROR_CODES as readonly string[]).includes(code) &&
    typeof message === 'string' &&
    (fieldErrors === undefined || Array.isArray(fieldErrors))
  );
}
