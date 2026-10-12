import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { FieldError } from './FieldError';
import { Message } from './Message';

describe('Message', () => {
  it('異常のメッセージをそのまま表示する', () => {
    render(<Message kind="error" message="入力に誤りがあります" />);

    expect(screen.getByRole('alert')).toHaveTextContent('入力に誤りがあります');
  });

  it('成功のメッセージをそのまま表示する', () => {
    render(<Message kind="success" message="パスワードを変更しました" />);

    expect(screen.getByRole('status')).toHaveTextContent('パスワードを変更しました');
  });

  it('メッセージが無ければ何も表示しない', () => {
    const { container } = render(<Message kind="error" message={null} />);

    expect(container).toBeEmptyDOMElement();
  });
});

describe('FieldError', () => {
  const errors = [
    { field: 'email', message: 'メールアドレスの形式で入力してください' },
    { field: 'password', message: 'パスワードは 12 文字以上で入力してください' },
  ];

  it('指定した入力の誤りだけを表示する', () => {
    render(<FieldError field="email" errors={errors} id="email-error" />);

    expect(screen.getByText('メールアドレスの形式で入力してください')).toBeInTheDocument();
    expect(screen.queryByText('パスワードは 12 文字以上で入力してください')).not.toBeInTheDocument();
  });

  it('誤りが無ければ何も表示しない', () => {
    const { container } = render(<FieldError field="displayName" errors={errors} />);

    expect(container).toBeEmptyDOMElement();
  });
});
