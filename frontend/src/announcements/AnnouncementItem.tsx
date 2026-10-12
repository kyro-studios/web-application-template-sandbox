import type { CSSProperties } from 'react'
import type { Announcement } from '../api/announcements'
import { formatPublishedDate } from './formatPublishedDate'

// 長い語で表示が崩れないように折り返す。文字数での省略はしない (アーキテクチャ設計 3.3)
const wrapStyle: CSSProperties = { overflowWrap: 'anywhere' }

// 本文は改行の位置と数を保つ (仕様書 8.3、FR-6)
const bodyStyle: CSSProperties = { ...wrapStyle, whiteSpace: 'pre-wrap' }

// タイトル、本文は JSX の式で文字列として出力する。HTML として解釈しない (NFR-1)
export function AnnouncementItem({ item }: { item: Announcement }) {
  return (
    <article>
      <h2 style={wrapStyle}>{item.title}</h2>
      <time dateTime={item.publishedDate}>{formatPublishedDate(item.publishedDate)}</time>
      <p style={bodyStyle}>{item.body}</p>
    </article>
  )
}
