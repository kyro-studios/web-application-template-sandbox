import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AnnouncementsPage } from './AnnouncementsPage'
import { ANNOUNCEMENTS_FAILURE_MESSAGE, NO_ANNOUNCEMENTS_MESSAGE } from './useAnnouncements'

const item = {
  title: 'メンテナンスのお知らせ',
  body: '10 月 20 日の 2 時から 4 時まで、停止します。',
  publishedDate: '2026-01-05',
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

// 応答を返さない取得 (取得中の状態を保つ)
function stubPendingFetch() {
  const fetchMock = vi.fn(() => new Promise<Response>(() => {}))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function stubFetchOnce(response: Response | Promise<Response>) {
  const fetchMock = vi.fn(() => Promise.resolve(response))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

// 画面を開く。同じパスへ移動するボタンを置き、location.key が変わる移動を試せるようにする
function renderPage() {
  function Navigate() {
    const navigate = useNavigate()
    return <button onClick={() => navigate('/announcements')}>お知らせへ移動</button>
  }

  return render(
    <MemoryRouter initialEntries={['/announcements']}>
      <Routes>
        <Route
          path="/announcements"
          element={
            <>
              <AnnouncementsPage />
              <Navigate />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  // AbortSignal.timeout は、偽のタイマーでは進まない。同じ働きを setTimeout で作る代替に差し替える
  vi.spyOn(AbortSignal, 'timeout').mockImplementation((ms) => {
    const controller = new AbortController()
    setTimeout(() => controller.abort(new DOMException('The operation timed out.', 'TimeoutError')), ms)
    return controller.signal
  })
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('AnnouncementsPage', () => {
  it('取得の応答が来るまでは、取得中の表示をする', () => {
    stubPendingFetch()

    renderPage()

    expect(screen.getByRole('status').textContent).toBe('お知らせを読み込んでいます')
    expect(screen.queryByRole('listitem')).toBeNull()
    expect(screen.queryByText(NO_ANNOUNCEMENTS_MESSAGE)).toBeNull()
  })

  it('1 件以上のお知らせを、応答の順序のまま、タイトル、公開日、本文で表示する', async () => {
    const second = { title: '新機能の案内', body: 'お知らせの一覧を追加しました。', publishedDate: '2026-10-12' }
    stubFetchOnce(jsonResponse({ announcements: [item, second] }))

    renderPage()

    expect(await screen.findByRole('heading', { level: 2, name: item.title })).toBeTruthy()
    const titles = screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)
    expect(titles).toEqual([item.title, second.title])
    expect(screen.getByText('2026年1月5日').tagName).toBe('TIME')
    expect(screen.getByText('2026年10月12日').tagName).toBe('TIME')
    expect(document.querySelector('p')?.textContent).toBe(item.body)
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.queryByText(NO_ANNOUNCEMENTS_MESSAGE)).toBeNull()
  })

  it('0 件の応答では、お知らせが無いことを表示する', async () => {
    stubFetchOnce(jsonResponse({ announcements: [] }))

    renderPage()

    expect(await screen.findByText(NO_ANNOUNCEMENTS_MESSAGE)).toBeTruthy()
    expect(screen.queryByRole('heading', { level: 2 })).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it.each([
    ['HTTP の状態が 503', () => Promise.resolve(jsonResponse({ error: { code: 'E-04', message: ANNOUNCEMENTS_FAILURE_MESSAGE } }, 503))],
    ['ネットワークの失敗', () => Promise.reject(new TypeError('Failed to fetch'))],
    ['本文が壊れた JSON', () => Promise.resolve(new Response('{"announcements":[', { status: 200 }))],
    ['応答の形が違う (announcements が配列でない)', () => Promise.resolve(jsonResponse({ announcements: 'お知らせ' }))],
    ['応答の要素に title が無い', () => Promise.resolve(jsonResponse({ announcements: [{ body: item.body, publishedDate: item.publishedDate }] }))],
  ])('%s は、失敗の文言を表示し、お知らせ無しの表示にしない', async (_name, respond) => {
    stubFetchOnce(respond())

    renderPage()

    expect((await screen.findByRole('alert')).textContent).toBe(ANNOUNCEMENTS_FAILURE_MESSAGE)
    expect(screen.queryByText(NO_ANNOUNCEMENTS_MESSAGE)).toBeNull()
    expect(screen.queryByRole('heading', { level: 2 })).toBeNull()
    // 通信の内容や例外の内容は、画面に出さない
    expect(document.body.textContent).not.toContain('Failed to fetch')
    expect(document.body.textContent).not.toContain('JSON')
  })

  it('5 秒を過ぎても応答が返らないと、失敗の文言を表示する', async () => {
    vi.useFakeTimers()
    // 中止されると失敗する (実際の fetch と同じ)
    vi.stubGlobal(
      'fetch',
      vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
        return new Promise<Response>((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(init.signal?.reason))
        })
      }),
    )

    renderPage()
    await act(async () => {
      vi.advanceTimersByTime(4999)
    })
    expect(screen.getByRole('status')).toBeTruthy()

    await act(async () => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.getByRole('alert').textContent).toBe(ANNOUNCEMENTS_FAILURE_MESSAGE)
    expect(screen.queryByText(NO_ANNOUNCEMENTS_MESSAGE)).toBeNull()
  })

  it('タイトルと本文に HTML を含んでも、文字のまま表示し、script と img の要素を増やさない', async () => {
    const hostile = {
      title: '<script>alert(1)</script>',
      body: '<img src=x onerror=alert(1)>\nhttps://example.com',
      publishedDate: '2026-01-05',
    }
    stubFetchOnce(jsonResponse({ announcements: [hostile] }))

    const { container } = renderPage()

    expect(await screen.findByRole('heading', { level: 2, name: hostile.title })).toBeTruthy()
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    // URL は、リンクにせず文字のまま表示する
    expect(container.querySelector('a')).toBeNull()
    expect(container.querySelector('p')?.textContent).toBe(hostile.body)
  })

  it('本文の改行を保ち、長いタイトルと本文を省略せずに折り返す', async () => {
    const longTitle = 'タイトル'.repeat(20)
    const longBody = `1 行目\n\n3 行目の文字${'あ'.repeat(1500)}\n終わり`
    stubFetchOnce(jsonResponse({ announcements: [{ title: longTitle, body: longBody, publishedDate: '2026-01-05' }] }))

    const { container } = renderPage()

    const heading = await screen.findByRole('heading', { level: 2 })
    const paragraph = container.querySelector('p') as HTMLElement
    expect(heading.textContent).toBe(longTitle)
    expect(paragraph.textContent).toBe(longBody)
    expect(paragraph.style.whiteSpace).toBe('pre-wrap')
    expect(heading.style.overflowWrap).toBe('anywhere')
    expect(paragraph.style.overflowWrap).toBe('anywhere')
    // 文字数での省略はしない
    expect(paragraph.getAttribute('style')).not.toMatch(/text-overflow|line-clamp/)
  })

  it('同じパスへ移動すると (location.key が変わると)、もう一度取得する', async () => {
    const fetchMock = stubFetchOnce(jsonResponse({ announcements: [item] }))
    renderPage()
    expect(await screen.findByRole('heading', { level: 2, name: item.title })).toBeTruthy()

    fetchMock.mockImplementation(() => new Promise<Response>(() => {}))
    fireEvent.click(screen.getByRole('button', { name: 'お知らせへ移動' }))

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('status')).toBeTruthy()
    expect(screen.queryByRole('heading', { level: 2 })).toBeNull()
  })
})
