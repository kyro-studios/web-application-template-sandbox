import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { FieldError } from './FieldError';
import { Message } from './Message';

describe('FieldError', () => {
  const errors = [
    { field: 'email', message: 'メールアドレスの形式で入力してください' },
    { field: 'password', message: 'パスワードは 12 文字以上で入力してください' },
  ];

  it('指定した入力の誤りだけを、API の message のまま表示する', () => {
    render(<FieldError field="email" errors={errors} id="email-error" />);

    const element = screen.getByText('メールアドレスの形式で入力してください');
    expect(element).toHaveAttribute('id', 'email-error');
    expect(screen.queryByText('パスワードは 12 文字以上で入力してください')).not.toBeInTheDocument();
  });

  it('誤りが無ければ何も表示しない', () => {
    const { container } = render(<FieldError field="displayName" errors={errors} />);

    expect(container).toBeEmptyDOMElement();
  });
});

describe('Message', () => {
  it('異常のメッセージを alert として表示する', () => {
    render(<Message type="error" text="入力に誤りがあります" />);

    expect(screen.getByRole('alert')).toHaveTextContent('入力に誤りがあります');
  });

  it('成功のメッセージを status として表示する', () => {
    render(<Message type="success" text="パスワードを変更しました" />);

    expect(screen.getByRole('status')).toHaveTextContent('パスワードを変更しました');
  });

  it('メッセージが無ければ何も表示しない', () => {
    const { container } = render(<Message type="error" text={null} />);

    expect(container).toBeEmptyDOMElement();
  });
});
