import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderApp, stubFetch } from '../test/app';
import { E06_MESSAGE, errorResponse, jsonResponse, unauthorizedResponse } from '../test/http';

const SUCCESS_MESSAGE = 'パスワードを変更しました';

async function openPage() {
  renderApp('/account/password');
  await screen.findByRole('heading', { name: 'パスワードの変更' });
}

function currentPasswordInput(): HTMLInputElement {
  return screen.getByLabelText('今のパスワード');
}

function newPasswordInput(): HTMLInputElement {
  return screen.getByLabelText('新しいパスワード');
}

function fillAndSubmit(currentPassword: string, newPassword: string) {
  fireEvent.change(currentPasswordInput(), { target: { value: currentPassword } });
  fireEvent.change(newPasswordInput(), { target: { value: newPassword } });
  fireEvent.click(screen.getByRole('button', { name: '変更する' }));
}

describe('PasswordPage', () => {
  it('今のパスワードと新しいパスワードの入力欄 (type="password") と、送信のボタンを表示する', async () => {
    stubFetch(() => Promise.reject(new Error('想定外の要求')));

    await openPage();

    expect(currentPasswordInput()).toHaveAttribute('type', 'password');
    expect(newPasswordInput()).toHaveAttribute('type', 'password');
    expect(currentPasswordInput()).not.toBeRequired();
    expect(newPasswordInput()).not.toHaveAttribute('maxLength');
    expect(screen.getByRole('button', { name: '変更する' })).toBeInTheDocument();
    expect(currentPasswordInput().closest('form')).toHaveAttribute('novalidate');
  });

  it('PUT /api/me/password を呼び、成功の message を表示し、入力欄を空にする', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(jsonResponse(200, { message: SUCCESS_MESSAGE })));
    await openPage();

    fillAndSubmit('correct horse battery', 'another long passphrase');

    expect(await screen.findByRole('status')).toHaveTextContent(SUCCESS_MESSAGE);
    expect(currentPasswordInput()).toHaveValue('');
    expect(newPasswordInput()).toHaveValue('');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/me/password',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify({ currentPassword: 'correct horse battery', newPassword: 'another long passphrase' }),
      }),
    );
    expect(screen.getByTestId('location')).toHaveTextContent('/account/password');
  });

  it('E-01 で、両方の入力の fieldErrors と message を表示し、入力欄を空にする', async () => {
    stubFetch(() =>
      Promise.resolve(
        errorResponse(400, 'E-01', '入力に誤りがあります', [
          { field: 'currentPassword', message: '今のパスワードを入力してください' },
          { field: 'newPassword', message: 'パスワードは 12 文字以上で入力してください' },
        ]),
      ),
    );
    await openPage();

    fillAndSubmit('', 'short');

    expect(await screen.findByRole('alert')).toHaveTextContent('入力に誤りがあります');
    expect(screen.getByText('今のパスワードを入力してください')).toHaveAttribute('id', 'currentPassword-error');
    expect(screen.getByText('パスワードは 12 文字以上で入力してください')).toHaveAttribute('id', 'newPassword-error');
    expect(currentPasswordInput()).toHaveValue('');
    expect(newPasswordInput()).toHaveValue('');
  });

  it('E-02 で message を表示し、入力欄を空にする', async () => {
    stubFetch(() => Promise.resolve(errorResponse(400, 'E-02', 'パスワードが正しくありません')));
    await openPage();

    fillAndSubmit('wrong password', 'another long passphrase');

    expect(await screen.findByRole('alert')).toHaveTextContent('パスワードが正しくありません');
    expect(currentPasswordInput()).toHaveValue('');
    expect(newPasswordInput()).toHaveValue('');
  });

  it('前の結果のメッセージは、次の結果で置き換える', async () => {
    let count = 0;
    stubFetch(() =>
      Promise.resolve(
        count++ === 0
          ? errorResponse(400, 'E-02', 'パスワードが正しくありません')
          : jsonResponse(200, { message: SUCCESS_MESSAGE }),
      ),
    );
    await openPage();

    fillAndSubmit('wrong password', 'another long passphrase');
    expect(await screen.findByRole('alert')).toHaveTextContent('パスワードが正しくありません');

    fillAndSubmit('correct horse battery', 'another long passphrase');
    expect(await screen.findByRole('status')).toHaveTextContent(SUCCESS_MESSAGE);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('E-06 で未ログインになり /login へ移り、E-06 の message を表示する', async () => {
    stubFetch(() => Promise.resolve(unauthorizedResponse()));
    await openPage();

    fillAndSubmit('correct horse battery', 'another long passphrase');

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/login'));
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });
});
