// 公開日 (YYYY-MM-DD) を、画面の表示 (2026年1月5日) に変える (仕様書 5.2、8.4)
// Date に変えると、ブラウザーのタイムゾーンで日付がずれ得るため、文字列の部分だけを使う
export function formatPublishedDate(publishedDate: string): string {
  const [year, month, day] = publishedDate.split('-')
  return `${Number(year)}年${Number(month)}月${Number(day)}日`
}
