import { AnnouncementItem } from './AnnouncementItem'
import { ANNOUNCEMENTS_FAILURE_MESSAGE, NO_ANNOUNCEMENTS_MESSAGE, useAnnouncements } from './useAnnouncements'

// お知らせの一覧の画面 (仕様書 9.2)。取得中、一覧表示、お知らせ無し、取得失敗のどれか 1 つを表示する
// 一覧は応答の順序のまま表示する。並べ替えや件数の絞り込みはしない (アーキテクチャ設計 3.3)
export function AnnouncementsPage() {
  const state = useAnnouncements()

  return (
    <>
      <h1>お知らせ</h1>
      {state.status === 'loading' && <p role="status">お知らせを読み込んでいます</p>}
      {state.status === 'failure' && <p role="alert">{ANNOUNCEMENTS_FAILURE_MESSAGE}</p>}
      {state.status === 'success' && state.items.length === 0 && <p>{NO_ANNOUNCEMENTS_MESSAGE}</p>}
      {state.status === 'success' && state.items.length > 0 && (
        <ul>
          {state.items.map((item, index) => (
            <li key={index}>
              <AnnouncementItem item={item} />
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
