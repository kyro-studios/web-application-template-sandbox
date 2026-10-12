import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchAnnouncements } from './announcements'

const item = {
  title: 'メンテナンスのお知らせ',
  body: '10 月 20 日の 2 時から 4 時まで、停止します。\n\nご不便をおかけします。',
  publishedDate: '2026-10-12',
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function stubFetch(response: () => Response | Promise<Response>) {
  const fetchMock = vi.fn(() => Promise.resolve().then(response))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function signal(): AbortSignal {
  return new AbortController().signal
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('fetchAnnouncements', () => {
  it('GET /api/announcements を呼び、中止のための signal を渡す', async () => {
    const fetchMock = stubFetch(() => jsonResponse({ announcements: [] }))
    const controller = new AbortController()

    await fetchAnnouncements(controller.signal)

    expect(fetchMock).toHaveBeenCalledWith('/api/announcements', { signal: controller.signal })
  })

  it('1 件以上のお知らせを、応答の順序のまま返す', async () => {
    const second = { title: '新機能の案内', body: 'お知らせの一覧を追加しました。', publishedDate: '2026-10-01' }
    stubFetch(() => jsonResponse({ announcements: [item, second] }))

    await expect(fetchAnnouncements(signal())).resolves.toEqual([item, second])
  })

  it('0 件の応答は、空の配列として成功にする', async () => {
    stubFetch(() => jsonResponse({ announcements: [] }))

    await expect(fetchAnnouncements(signal())).resolves.toEqual([])
  })

  it('20 件までは成功にする', async () => {
    const items = Array.from({ length: 20 }, () => item)
    stubFetch(() => jsonResponse({ announcements: items }))

    await expect(fetchAnnouncements(signal())).resolves.toHaveLength(20)
  })

  it('HTTP の状態が 200 以外なら、本文の形が正しくても失敗にする', async () => {
    stubFetch(() => jsonResponse({ announcements: [item] }, 503))

    await expect(fetchAnnouncements(signal())).rejects.toThrow()
  })

  it.each([201, 404, 500, 503])('HTTP の状態 %i は、失敗にする', async (status) => {
    stubFetch(() => jsonResponse({ error: { code: 'E-04', message: 'お知らせを表示できませんでした' } }, status))

    await expect(fetchAnnouncements(signal())).rejects.toThrow()
  })

  it('ネットワークの失敗なら失敗にする', async () => {
    stubFetch(() => Promise.reject(new TypeError('Failed to fetch')))

    await expect(fetchAnnouncements(signal())).rejects.toThrow()
  })

  it('本文が JSON として読めなければ失敗にする', async () => {
    stubFetch(() => new Response('{"announcements":[', { status: 200 }))

    await expect(fetchAnnouncements(signal())).rejects.toThrow()
  })

  it.each([
    ['本文が配列', []],
    ['本文が null', null],
    ['announcements が無い', {}],
    ['announcements が配列ではない', { announcements: 'お知らせ' }],
    ['21 件ある', { announcements: Array.from({ length: 21 }, () => item) }],
    ['要素が配列ではなく文字列', { announcements: ['お知らせ'] }],
    ['要素の title が無い', { announcements: [{ body: item.body, publishedDate: item.publishedDate }] }],
    ['要素の title が文字列ではない', { announcements: [{ ...item, title: 1 }] }],
    ['要素の body が null', { announcements: [{ ...item, body: null }] }],
    ['要素の publishedDate が文字列ではない', { announcements: [{ ...item, publishedDate: 20261012 }] }],
    ['publishedDate が YYYY-MM-DD ではない (スラッシュ)', { announcements: [{ ...item, publishedDate: '2026/10/12' }] }],
    ['publishedDate が YYYY-MM-DD ではない (1 桁の月)', { announcements: [{ ...item, publishedDate: '2026-1-5' }] }],
    [
      'publishedDate が日時の形',
      { announcements: [{ ...item, publishedDate: '2026-10-12T00:00:00+09:00' }] },
    ],
    ['正しい要素と、形の違う要素が混ざる', { announcements: [item, { ...item, title: null }] }],
  ])('形が違う応答 (%s) は、一部を使わず失敗にする', async (_name, body) => {
    stubFetch(() => jsonResponse(body))

    await expect(fetchAnnouncements(signal())).rejects.toThrow()
  })
})
