import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderApp, stubFetch } from '../test/app';
import { E06_MESSAGE, errorResponse, jsonResponse, unauthorizedResponse } from '../test/http';

const SUCCESS_MESSAGE = 'アカウントを削除し、ログアウトしました';

async function openPage() {
  renderApp('/account/delete');
  await screen.findByRole('heading', { name: 'アカウントの削除' });
}

function passwordInput(): HTMLInputElement {
  return screen.getByLabelText('パスワード');
}

function fillAndSubmit(password: string) {
  fireEvent.change(passwordInput(), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: '削除する' }));
}

describe('DeleteAccountPage', () => {
  it('パスワードの入力欄 (type="password") と、送信のボタンを表示する', async () => {
    stubFetch(() => Promise.reject(new Error('想定外の要求')));

    await openPage();

    expect(passwordInput()).toHaveAttribute('type', 'password');
    expect(passwordInput()).not.toBeRequired();
    expect(screen.getByRole('button', { name: '削除する' })).toBeInTheDocument();
    expect(passwordInput().closest('form')).toHaveAttribute('novalidate');
  });

  it('POST /api/me/delete を呼び、成功したら未ログインにして /login へ移り、message を表示する', async () => {
    const fetchMock = stubFetch(() => Promise.resolve(jsonResponse(200, { message: SUCCESS_MESSAGE })));
    await openPage();
    const input = passwordInput();

    fillAndSubmit('correct horse battery');

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('location').textContent).toBe('/login');
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.getByRole('status')).toHaveTextContent(SUCCESS_MESSAGE);
    expect(input).toHaveValue('');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/me/delete',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ password: 'correct horse battery' }) }),
    );
  });

  it('E-01 で fieldErrors と message を表示し、入力欄を空にする', async () => {
    stubFetch(() =>
      Promise.resolve(
        errorResponse(400, 'E-01', '入力に誤りがあります', [
          { field: 'password', message: 'パスワードを入力してください' },
        ]),
      ),
    );
    await openPage();

    fillAndSubmit('');

    expect(await screen.findByRole('alert')).toHaveTextContent('入力に誤りがあります');
    expect(screen.getByText('パスワードを入力してください')).toHaveAttribute('id', 'password-error');
    expect(passwordInput()).toHaveValue('');
    expect(screen.getByTestId('location')).toHaveTextContent('/account/delete');
    expect(screen.getByTestId('auth')).toHaveTextContent('authenticated');
  });

  it('E-02 で message を表示し、入力欄を空にする', async () => {
    stubFetch(() => Promise.resolve(errorResponse(400, 'E-02', 'パスワードが正しくありません')));
    await openPage();

    fillAndSubmit('wrong password');

    expect(await screen.findByRole('alert')).toHaveTextContent('パスワードが正しくありません');
    expect(passwordInput()).toHaveValue('');
    expect(screen.getByTestId('location')).toHaveTextContent('/account/delete');
  });

  it('E-06 で未ログインになり /login へ移り、E-06 の message を表示する', async () => {
    stubFetch(() => Promise.resolve(unauthorizedResponse()));
    await openPage();

    fillAndSubmit('correct horse battery');

    expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument();
    expect(screen.getByTestId('location').textContent).toBe('/login');
    expect(screen.getByTestId('auth')).toHaveTextContent('unauthenticated');
    expect(screen.getByRole('alert')).toHaveTextContent(E06_MESSAGE);
  });
});
