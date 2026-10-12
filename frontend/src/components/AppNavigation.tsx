import { Link } from 'react-router'

// すべての画面に共通のナビゲーション (アーキテクチャ設計 3.3、FR-9)
// ログインの有無に関係なく、同じ内容で表示する。認証の状態を読まないため、AuthContext などは import しない
// 認証機能のナビゲーションの項目 (ログイン、アカウントなど) は、この中に項目を足す形で加える
export function AppNavigation() {
  return (
    <nav aria-label="共通のナビゲーション">
      <ul>
        <li>
          <Link to="/announcements">お知らせ</Link>
        </li>
      </ul>
    </nav>
  )
}
