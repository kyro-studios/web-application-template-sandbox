// API の要求と応答の型 (アーキテクチャ設計 5 章)

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
export interface ApiFieldError {
  field: string;
  message: string;
}

/** エラーの応答 (5.2)。fieldErrors は E-01 のときだけ付く */
export interface ErrorResponse {
  error: {
    code: ErrorCode;
    message: string;
    fieldErrors?: ApiFieldError[];
  };
}

/** ログイン中のアカウントの表示名とメールアドレス */
export interface Account {
  email: string;
  displayName: string;
}

/** POST /api/accounts (OP-1 利用者登録) */
export interface RegisterRequest {
  email: string;
  displayName: string;
  password: string;
}

export interface RegisterResponse {
  message: string;
  account: Account;
}

/** POST /api/session (OP-2 ログイン) */
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  message: string;
  account: Pick<Account, 'displayName'>;
}

/** DELETE /api/session (OP-3 ログアウト) */
export interface LogoutResponse {
  message: string;
}

/** GET /api/me (OP-4 自分のアカウントの情報を見る) */
export interface MeResponse {
  account: Account;
}

/** PUT /api/me/profile (OP-5 表示名とメールアドレスの変更) */
export interface UpdateProfileRequest {
  email: string;
  displayName: string;
}

export interface UpdateProfileResponse {
  message: string;
  account: Account;
}

/** PUT /api/me/password (OP-6 パスワードの変更) */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ChangePasswordResponse {
  message: string;
}

/** POST /api/me/delete (OP-7 アカウントの削除) */
export interface DeleteAccountRequest {
  password: string;
}

export interface DeleteAccountResponse {
  message: string;
}
