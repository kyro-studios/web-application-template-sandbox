import { request, type RequestOptions } from './client';
import type {
  ChangePasswordRequest,
  ChangePasswordResponse,
  DeleteAccountRequest,
  DeleteAccountResponse,
  LoginRequest,
  LoginResponse,
  LogoutResponse,
  MeResponse,
  RegisterRequest,
  RegisterResponse,
  UpdateProfileRequest,
  UpdateProfileResponse,
} from './types';

/** OP-1 利用者登録 */
export function register(input: RegisterRequest): Promise<RegisterResponse> {
  return request<RegisterResponse>('POST', '/api/accounts', input);
}

/** OP-2 ログイン */
export function login(input: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>('POST', '/api/session', input);
}

/** OP-3 ログアウト */
export function logout(): Promise<LogoutResponse> {
  return request<LogoutResponse>('DELETE', '/api/session');
}

/** OP-4 自分のアカウントの情報を見る */
export function getMe(options?: RequestOptions): Promise<MeResponse> {
  return request<MeResponse>('GET', '/api/me', undefined, options);
}

/** OP-5 表示名とメールアドレスの変更 */
export function updateProfile(input: UpdateProfileRequest): Promise<UpdateProfileResponse> {
  return request<UpdateProfileResponse>('PUT', '/api/me/profile', input);
}

/** OP-6 パスワードの変更 */
export function changePassword(input: ChangePasswordRequest): Promise<ChangePasswordResponse> {
  return request<ChangePasswordResponse>('PUT', '/api/me/password', input);
}

/** OP-7 アカウントの削除 */
export function deleteAccount(input: DeleteAccountRequest): Promise<DeleteAccountResponse> {
  return request<DeleteAccountResponse>('POST', '/api/me/delete', input);
}
