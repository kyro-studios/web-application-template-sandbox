import { useLocation } from 'react-router';
import { Message } from '../components/Message';

/** /account へ移るときに渡す、画面の遷移の状態 (OP-1、OP-2 の成功など) */
export interface AccountPageState {
  /** 表示する成功のメッセージ (API の message のまま) */
  successMessage: string;
}

/** OP-4 と OP-5 自分のアカウントの情報の表示と変更 (仮。中身は後のタスクで作る) */
export function AccountPage() {
  const location = useLocation();
  const pageState = location.state as Partial<AccountPageState> | null;

  return (
    <main>
      <h1>アカウントの情報</h1>
      <Message type="success" text={pageState?.successMessage} />
    </main>
  );
}
