// お知らせの一覧の応答 (アーキテクチャ設計 4.2)。画面に出す 1 件の形
export type Announcement = {
  title: string
  body: string
  publishedDate: string
}

// 応答の件数の上限 (仕様書 6.3、アーキテクチャ設計 4.2)
const MAX_ANNOUNCEMENTS = 20

// 公開日は YYYY-MM-DD の形で届く (アーキテクチャ設計 4.2)
const PUBLISHED_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

// 認証機能の api/client.ts は使わず、独立して fetch する。E-06 による /login への移動を、この API に適用しないため (アーキテクチャ設計 8.7)
export async function fetchAnnouncements(signal: AbortSignal): Promise<Announcement[]> {
  const response = await fetch('/api/announcements', { signal })
  if (response.status !== 200) {
    throw new Error(`お知らせの取得に失敗しました (HTTP ${response.status})`)
  }

  const body: unknown = await response.json()
  return parseAnnouncements(body)
}

// 形が違う応答は、一部の要素だけを使わず、すべて失敗にする (仕様書 7.4、R-2)
function parseAnnouncements(body: unknown): Announcement[] {
  if (!isRecord(body) || !Array.isArray(body.announcements)) {
    throw new Error('お知らせの応答の形が違います')
  }
  if (body.announcements.length > MAX_ANNOUNCEMENTS) {
    throw new Error('お知らせの件数が上限を超えています')
  }
  return body.announcements.map(parseAnnouncement)
}

function parseAnnouncement(item: unknown): Announcement {
  if (
    !isRecord(item) ||
    typeof item.title !== 'string' ||
    typeof item.body !== 'string' ||
    typeof item.publishedDate !== 'string' ||
    !PUBLISHED_DATE_PATTERN.test(item.publishedDate)
  ) {
    throw new Error('お知らせの応答の形が違います')
  }
  return { title: item.title, body: item.body, publishedDate: item.publishedDate }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}
