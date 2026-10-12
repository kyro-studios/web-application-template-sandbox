import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, useNavigate } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ANNOUNCEMENTS_FAILURE_MESSAGE, NO_ANNOUNCEMENTS_MESSAGE, useAnnouncements } from './useAnnouncements'

const item = {
  title: 'メンテナンスのお知らせ',
  body: '10 月 20 日の 2 時から 4 時まで、停止します。',
  publishedDate: '2026-10-12',
}

const loading = { status: 'loading' }

type Pending = {
  signal: AbortSignal | null | undefined
  resolve: (response: Response) => void
  reject: (reason: unknown) => void
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

// fetch を差し替える。応答は、試験の中で好きなタイミングで返す。honorAbort が true のときは、実際の fetch と同じく、中止されると失敗する
function stubFetch({ honorAbort = true } = {}) {
  const pending: Pending[] = []
  const fetchMock = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
    return new Promise<Response>((resolve, reject) => {
      const signal = init?.signal
      if (honorAbort) {
        signal?.addEventListener('abort', () => reject(signal.reason))
      }
      pending.push({ signal, resolve, reject })
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return { fetchMock, pending }
}

// 画面を開いた状態を作る。navigate で、同じ URL への移動 (location.key が変わる) もできる
function renderAnnouncements(path = '/announcements') {
  const wrapper = ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={[path]}>{children}</MemoryRouter>
  return renderHook(
    () => ({ state: useAnnouncements(), navigate: useNavigate() }),
    { wrapper },
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

describe('useAnnouncements', () => {
  it('取得の応答が来るまでは、取得中の状態にする', () => {
    stubFetch()

    const { result } = renderAnnouncements()

    expect(result.current.state).toEqual(loading)
  })

  it('1 件以上の応答を、成功の状態で返す', async () => {
    const { pending } = stubFetch()
    const { result } = renderAnnouncements()

    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [item, { ...item, title: '新機能の案内' }] }))
    })

    expect(result.current.state).toEqual({ status: 'success', items: [item, { ...item, title: '新機能の案内' }] })
  })

  it('0 件の応答を、0 件の成功の状態で返す', async () => {
    const { pending } = stubFetch()
    const { result } = renderAnnouncements()

    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [] }))
    })

    expect(result.current.state).toEqual({ status: 'success', items: [] })
  })

  it.each([
    ['HTTP の状態が 503', () => jsonResponse({ error: { code: 'E-04', message: ANNOUNCEMENTS_FAILURE_MESSAGE } }, 503)],
    ['本文が壊れた JSON', () => new Response('{"announcements":[', { status: 200 })],
    ['要素の title が無い', () => jsonResponse({ announcements: [{ body: item.body, publishedDate: item.publishedDate }] })],
    ['要素の publishedDate が形の違う', () => jsonResponse({ announcements: [{ ...item, publishedDate: '2026/10/12' }] })],
    ['21 件ある', () => jsonResponse({ announcements: Array.from({ length: 21 }, () => item) })],
  ])('%s は、失敗の状態にし、0 件の成功にはしない', async (_name, respond) => {
    const { pending } = stubFetch()
    const { result } = renderAnnouncements()

    await act(async () => {
      pending[0].resolve(respond())
    })

    expect(result.current.state).toEqual({ status: 'failure' })
  })

  it('ネットワークの失敗は、失敗の状態にし、0 件の成功にはしない', async () => {
    const { pending } = stubFetch()
    const { result } = renderAnnouncements()

    await act(async () => {
      pending[0].reject(new TypeError('Failed to fetch'))
    })

    expect(result.current.state).toEqual({ status: 'failure' })
  })

  it('5 秒を過ぎると失敗の状態にし、その後に届いた内容は使わない', async () => {
    vi.useFakeTimers()
    const { pending } = stubFetch()
    const { result } = renderAnnouncements()

    await act(async () => {
      vi.advanceTimersByTime(4999)
    })
    expect(result.current.state).toEqual(loading)

    await act(async () => {
      vi.advanceTimersByTime(1)
    })
    expect(result.current.state).toEqual({ status: 'failure' })

    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [item] }))
    })
    expect(result.current.state).toEqual({ status: 'failure' })
  })

  it('ヘッダーは届いたが本文の読み込みが 5 秒を過ぎると、失敗の状態にする', async () => {
    vi.useFakeTimers()
    // 本文は届かないまま止まる。中止されると、本文の読み込みも失敗する (実際の fetch と同じ)
    vi.stubGlobal(
      'fetch',
      vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
        const stream = new ReadableStream<Uint8Array>({
          start(controller) {
            init?.signal?.addEventListener('abort', () => controller.error(init.signal?.reason))
          },
        })
        return Promise.resolve(new Response(stream, { status: 200 }))
      }),
    )
    const { result } = renderAnnouncements()

    await act(async () => {
      vi.advanceTimersByTime(4999)
    })
    expect(result.current.state).toEqual(loading)

    await act(async () => {
      vi.advanceTimersByTime(1)
    })
    expect(result.current.state).toEqual({ status: 'failure' })
  })

  it('中止を無視する本文の読み込みでも、5 秒を過ぎてから届いた内容は使わない', async () => {
    vi.useFakeTimers()
    let body: ReadableStreamDefaultController<Uint8Array> | undefined
    vi.stubGlobal(
      'fetch',
      vi.fn(() => {
        const stream = new ReadableStream<Uint8Array>({
          start(controller) {
            body = controller
          },
        })
        return Promise.resolve(new Response(stream, { status: 200 }))
      }),
    )
    const { result } = renderAnnouncements()

    await act(async () => {
      vi.advanceTimersByTime(5000)
    })
    await act(async () => {
      body?.enqueue(new TextEncoder().encode(JSON.stringify({ announcements: [item] })))
      body?.close()
    })

    expect(result.current.state).toEqual({ status: 'failure' })
  })

  it('中止を無視する取得でも、5 秒を過ぎてから届いた内容は使わない', async () => {
    vi.useFakeTimers()
    const { pending } = stubFetch({ honorAbort: false })
    const { result } = renderAnnouncements()

    await act(async () => {
      vi.advanceTimersByTime(5000)
    })
    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [item] }))
    })

    expect(result.current.state).toEqual({ status: 'failure' })
  })

  it('画面を開き直すと、もう一度取得し、古い取得の結果で状態を更新しない', async () => {
    // 中止を無視する取得にして、古い取得の応答が遅れて届く場合を試す
    const { fetchMock, pending } = stubFetch({ honorAbort: false })
    const { result } = renderAnnouncements()

    act(() => {
      result.current.navigate('/announcements')
    })
    expect(fetchMock).toHaveBeenCalledTimes(2)

    await act(async () => {
      pending[1].resolve(jsonResponse({ announcements: [{ ...item, title: '新しいお知らせ' }] }))
    })
    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [{ ...item, title: '古いお知らせ' }] }))
    })

    expect(result.current.state).toEqual({ status: 'success', items: [{ ...item, title: '新しいお知らせ' }] })
  })

  it('location.key が変わると、もう一度取得し、取得の間は取得中の状態にする', async () => {
    const { fetchMock, pending } = stubFetch()
    const { result } = renderAnnouncements()
    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [item] }))
    })
    expect(result.current.state).toEqual({ status: 'success', items: [item] })

    act(() => {
      result.current.navigate('/announcements')
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(result.current.state).toEqual(loading)
  })

  it('同じ URL へ移動したあと、取得の完了前に戻っても、前の画面の結果を使わず、取得中の状態にする', async () => {
    const { fetchMock, pending } = stubFetch()
    const { result } = renderAnnouncements()
    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [item] }))
    })
    expect(result.current.state).toEqual({ status: 'success', items: [item] })

    act(() => {
      result.current.navigate('/announcements')
    })
    expect(fetchMock).toHaveBeenCalledTimes(2)

    // history の「戻る」で、最初の画面の location.key に戻る
    act(() => {
      result.current.navigate(-1)
    })

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(result.current.state).toEqual(loading)
  })

  it('画面から移動すると、進行中の取得を中止する', () => {
    const { pending } = stubFetch()
    const { result, unmount } = renderAnnouncements()
    expect(pending[0].signal?.aborted).toBe(false)

    unmount()

    expect(pending[0].signal?.aborted).toBe(true)
    expect(result.current.state).toEqual(loading)
  })

  it('開き直すたびに取得し、前回の結果を再利用しない', async () => {
    const { fetchMock, pending } = stubFetch()
    const first = renderAnnouncements()
    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [item] }))
    })
    first.unmount()

    const second = renderAnnouncements()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(second.result.current.state).toEqual(loading)
  })

  it('時間が経っても、自動で取得し直さない', async () => {
    vi.useFakeTimers()
    const { fetchMock, pending } = stubFetch()
    renderAnnouncements()
    await act(async () => {
      pending[0].resolve(jsonResponse({ announcements: [item] }))
    })

    await act(async () => {
      vi.advanceTimersByTime(60_000)
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('表示の文言', () => {
  it('失敗の文言は、E-04 の message と同じ文字列である', () => {
    expect(ANNOUNCEMENTS_FAILURE_MESSAGE).toBe('お知らせを表示できませんでした。時間をおいて、もう一度開いてください')
  })

  it('お知らせ無しの文言は、仕様どおりの文字列である', () => {
    expect(NO_ANNOUNCEMENTS_MESSAGE).toBe('公開中のお知らせはありません')
  })
})
