// API の要求と応答の型 (アーキテクチャ設計 5 章)

/** 異常の番号 (仕様書 7.1、アーキテクチャ設計 5.2) */
export type ErrorCode =
  | 'E-01'
  | 'E-02'
  | 'E-03'
  | 'E-04'
  | 'E-05'
  | 'E-06'
  | 'E-07'
  | 'E-08'
  | 'E-90';

/** 入力ごとの誤り (5.3) */
export interface FieldErrorItem {
  field: string;
  message: string;
}

/** エラーの応答の本文 (5.2)。fieldErrors は E-01 のときだけ付く */
export interface ErrorResponse {
  error: {
    code: ErrorCode;
    message: string;
    fieldErrors?: FieldErrorItem[];
  };
}

/** 応答に含まれるアカウントの情報 */
export interface Account {
  email: string;
  displayName: string;
}

/** 成功の応答のうち、知らせる内容だけを持つもの */
export interface MessageResponse {
  message: string;
}

// 5.6.1 POST /api/accounts (OP-1 利用者登録)
export interface RegisterRequest {
  email: string;
  displayName: string;
  password: string;
}

export interface RegisterResponse {
  message: string;
  account: Account;
}

// 5.6.2 POST /api/session (OP-2 ログイン)
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  message: string;
  account: Pick<Account, 'displayName'>;
}

// 5.6.3 DELETE /api/session (OP-3 ログアウト)
export type LogoutResponse = MessageResponse;

// 5.6.4 GET /api/me (OP-4 自分のアカウントの情報を見る)
export interface MeResponse {
  account: Account;
}

// 5.6.5 PUT /api/me/profile (OP-5 表示名とメールアドレスの変更)
export interface UpdateProfileRequest {
  email: string;
  displayName: string;
}

export interface UpdateProfileResponse {
  message: string;
  account: Account;
}

// 5.6.6 PUT /api/me/password (OP-6 パスワードの変更)
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export type ChangePasswordResponse = MessageResponse;

// 5.6.7 POST /api/me/delete (OP-7 アカウントの削除)
export interface DeleteAccountRequest {
  password: string;
}

export type DeleteAccountResponse = MessageResponse;
