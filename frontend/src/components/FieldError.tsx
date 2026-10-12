import type { ApiFieldError } from '../api/types';

interface FieldErrorProps {
  /** 入力の名前 (API の fieldErrors の field) */
  field: string;
  /** API の応答の fieldErrors */
  errors?: readonly ApiFieldError[];
  /** 入力欄の aria-describedby から参照するための id */
  id?: string;
}

/** 入力ごとの誤りの表示。API の応答の fieldErrors のうち、field が一致するものを表示する */
export function FieldError({ field, errors, id }: FieldErrorProps) {
  const messages = (errors ?? []).filter((error) => error.field === field).map((error) => error.message);
  if (messages.length === 0) {
    return null;
  }
  return (
    <div className="field-error" id={id}>
      {messages.map((message, index) => (
        <p key={index}>{message}</p>
      ))}
    </div>
  );
}
