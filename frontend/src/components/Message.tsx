export type MessageKind = 'success' | 'error';

interface MessageProps {
  kind: MessageKind;
  /** API の応答の message をそのまま渡す。空なら何も表示しない */
  message?: string | null;
}

/** 操作の結果 (成功、異常) の表示 */
export function Message({ kind, message }: MessageProps) {
  if (!message) {
    return null;
  }
  return (
    <p className={`message message-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {message}
    </p>
  );
}
