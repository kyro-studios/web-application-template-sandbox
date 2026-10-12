import type { ErrorCode } from '../api/types';

/** 試験用に、JSON の本文を持つ応答を作る */
export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** 試験用に、5.2 の形のエラーの応答を作る */
export function errorResponse(status: number, code: ErrorCode, message: string): Response {
  return jsonResponse(status, { error: { code, message } });
}

export const E06_MESSAGE = 'ログインが必要です';

export function unauthorizedResponse(): Response {
  return errorResponse(401, 'E-06', E06_MESSAGE);
}
