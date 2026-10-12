import type { ErrorCode, FieldErrorItem } from '../api/types';

/** JSON の本文を持つ応答を作る */
export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** エラーの応答 (アーキテクチャ設計 5.2) を作る */
export function errorResponse(
  status: number,
  code: ErrorCode,
  message: string,
  fieldErrors?: FieldErrorItem[],
): Response {
  return jsonResponse(status, { error: { code, message, ...(fieldErrors ? { fieldErrors } : {}) } });
}

export const E06_MESSAGE = 'ログインが必要です';

export function unauthorizedResponse(): Response {
  return errorResponse(401, 'E-06', E06_MESSAGE);
}
