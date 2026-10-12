import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

// 移動の先を確かめるため、現在のパスを表示する
function CurrentPath() {
  return <p data-testid="current-path">{useLocation().pathname}</p>
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('"/" を開くと、見出しを表示する', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Webアプリケーション')
  })

  it('"/announcements" を開くと、お知らせの一覧を表示する', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(jsonResponse({ announcements: [{ title: '新機能の案内', body: '本文', publishedDate: '2026-01-05' }] }))),
    )

    render(
      <MemoryRouter initialEntries={['/announcements']}>
        <App />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { level: 2, name: '新機能の案内' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('お知らせ')
  })

  it('ログインの確認 (GET /api/me) が E-06 でも、/login へ移らずに一覧を表示する', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      if (String(input) === '/api/me') {
        return Promise.resolve(jsonResponse({ error: { code: 'E-06', message: 'ログインが必要です' } }, 401))
      }
      return Promise.resolve(jsonResponse({ announcements: [{ title: '新機能の案内', body: '本文', publishedDate: '2026-01-05' }] }))
    })
    vi.stubGlobal('fetch', fetchMock)

    render(
      <MemoryRouter initialEntries={['/announcements']}>
        <App />
        <CurrentPath />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { level: 2, name: '新機能の案内' })).toBeTruthy()
    expect(screen.getByTestId('current-path').textContent).toBe('/announcements')
  })

  it('すべての画面に、共通のナビゲーションを表示する', () => {
    const { unmount } = render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )
    expect(screen.getByRole('navigation', { name: '共通のナビゲーション' })).toBeTruthy()
    unmount()

    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse({ announcements: [] }))))
    render(
      <MemoryRouter initialEntries={['/announcements']}>
        <App />
      </MemoryRouter>,
    )
    expect(screen.getByRole('navigation', { name: '共通のナビゲーション' })).toBeTruthy()
  })

  it('ほかの画面で項目「お知らせ」を選ぶと、一覧の画面へ移動し、取得する', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(jsonResponse({ announcements: [{ title: '新機能の案内', body: '本文', publishedDate: '2026-01-05' }] })))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
        <CurrentPath />
      </MemoryRouter>,
    )
    expect(fetchMock).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('link', { name: 'お知らせ' }))

    expect(screen.getByTestId('current-path').textContent).toBe('/announcements')
    expect(await screen.findByRole('heading', { level: 2, name: '新機能の案内' })).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('一覧の画面を見ている間に項目「お知らせ」を選び直すと、もう一度取得する', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(jsonResponse({ announcements: [{ title: '新機能の案内', body: '本文', publishedDate: '2026-01-05' }] })))
    vi.stubGlobal('fetch', fetchMock)

    render(
      <MemoryRouter initialEntries={['/announcements']}>
        <App />
        <CurrentPath />
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { level: 2, name: '新機能の案内' })).toBeTruthy()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('link', { name: 'お知らせ' }))

    expect(screen.getByTestId('current-path').textContent).toBe('/announcements')
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(await screen.findByRole('heading', { level: 2, name: '新機能の案内' })).toBeTruthy()
  })
})
