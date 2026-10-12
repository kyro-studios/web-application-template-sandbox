interface MessageProps {
  type: 'success' | 'error';
  /** API の応答の message。空なら何も表示しない */
  text: string | null | undefined;
}

/** 操作の結果 (成功、異常) を表示する。API の message をそのまま表示する */
export function Message({ type, text }: MessageProps) {
  if (!text) {
    return null;
  }
  return (
    <p className={`message message-${type}`} role={type === 'error' ? 'alert' : 'status'}>
      {text}
    </p>
  );
}
