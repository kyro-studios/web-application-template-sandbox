import { useEffect, useState } from 'react'
import { useLocation } from 'react-router'
import { type Announcement, fetchAnnouncements } from '../api/announcements'

// 応答が無い場合も表示するため、フロントエンドの定数として持つ。API の message には頼らない (アーキテクチャ設計 8.7)
export const ANNOUNCEMENTS_FAILURE_MESSAGE = 'お知らせを表示できませんでした。時間をおいて、もう一度開いてください'

export const NO_ANNOUNCEMENTS_MESSAGE = '公開中のお知らせはありません'

// 応答が返らないと判定するまでの上限 (仕様書 7.4、アーキテクチャ設計 7.2)
const FETCH_TIMEOUT_MS = 5000

export type State =
  | { status: 'loading' }
  | { status: 'success'; items: Announcement[] }
  | { status: 'failure' }

type Settled = Exclude<State, { status: 'loading' }>

const LOADING: State = { status: 'loading' }

// 画面を開いたとき、画面の移動で location.key が変わったときに取得する。結果は保存も再利用もしない
export function useAnnouncements(): State {
  const { key } = useLocation()
  // 取得のたびに新しい番号を作る。location.key は history の「戻る」で以前の値に戻るため、取得の区別には使わない
  const [request, setRequest] = useState(() => ({ key, id: {} }))
  if (request.key !== key) {
    setRequest({ key, id: {} })
  }
  // 結果は、取得を始めたときの番号と一緒に持つ。番号が変わったあとは、前の取得の結果を使わず、取得中とする
  const [result, setResult] = useState<{ id: object; state: Settled } | null>(null)

  useEffect(() => {
    const id = request.id
    const controller = new AbortController()
    const signal = AbortSignal.any([AbortSignal.timeout(FETCH_TIMEOUT_MS), controller.signal])

    fetchAnnouncements(signal).then(
      (items) => {
        // 中止された取得の結果では、状態を更新しない
        if (controller.signal.aborted) return
        // 上限時間を過ぎてから届いた内容は使わない
        setResult({ id, state: signal.aborted ? { status: 'failure' } : { status: 'success', items } })
      },
      () => {
        if (controller.signal.aborted) return
        setResult({ id, state: { status: 'failure' } })
      },
    )

    // 新しい取得を始めるとき、画面から移動したときに、取得を中止する
    return () => controller.abort()
  }, [request.id])

  return result?.id === request.id ? result.state : LOADING
}
