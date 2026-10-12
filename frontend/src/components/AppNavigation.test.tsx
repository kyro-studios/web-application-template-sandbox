import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AppNavigation } from './AppNavigation'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

// 認証機能は未実装のため、ログインの状態を Cookie と GET /api/me の応答で作る。AppNavigation がこれらを読まないことを確かめる
const authStates = {
  未ログイン: () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse({ error: { code: 'E-06', message: 'ログインが必要です' } }, 401))))
  },
  ログイン中: () => {
    document.cookie = 'SESSION=test-session'
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse({ id: 'user-1', name: '利用者' }))))
  },
}

function renderNavigation() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <AppNavigation />
    </MemoryRouter>,
  )
}

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'SESSION=; Max-Age=0'
})

describe('AppNavigation', () => {
  it('項目「お知らせ」を、/announcements へのリンクとして表示する', () => {
    renderNavigation()

    const link = screen.getByRole('link', { name: 'お知らせ' })
    expect(link.getAttribute('href')).toBe('/announcements')
  })

  it.each(Object.keys(authStates))('%s でも、項目「お知らせ」を表示する', (state) => {
    authStates[state as keyof typeof authStates]()

    renderNavigation()

    expect(screen.getAllByRole('link')).toHaveLength(1)
    expect(screen.getByRole('link', { name: 'お知らせ' }).getAttribute('href')).toBe('/announcements')
  })

  it('未ログインでも、ログイン中でも、表示の内容は同じである', () => {
    authStates['未ログイン']()
    const { container: loggedOut, unmount } = renderNavigation()
    const loggedOutMarkup = loggedOut.innerHTML
    unmount()

    authStates['ログイン中']()
    expect(document.cookie).toContain('SESSION=test-session')
    const { container: loggedIn } = renderNavigation()

    expect(loggedIn.innerHTML).toBe(loggedOutMarkup)
  })
})
