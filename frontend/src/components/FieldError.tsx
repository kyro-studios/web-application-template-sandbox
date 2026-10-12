import type { FieldErrorItem } from '../api/types';

interface FieldErrorProps {
  /** 入力の名前 (API の fieldErrors の field) */
  field: string;
  /** API の応答の fieldErrors */
  errors: readonly FieldErrorItem[] | undefined;
  /** 入力欄の aria-describedby から参照するための id */
  id?: string;
}

/** 入力ごとの誤りを表示する。API の message をそのまま表示する */
export function FieldError({ field, errors, id }: FieldErrorProps) {
  const messages = (errors ?? []).filter((error) => error.field === field).map((error) => error.message);
  if (messages.length === 0) {
    return null;
  }
  return (
    <p id={id} className="field-error">
      {messages.join(' ')}
    </p>
  );
}
