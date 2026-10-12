import { describe, expect, it } from 'vitest'
import { formatPublishedDate } from './formatPublishedDate'

describe('formatPublishedDate', () => {
  it.each([
    ['2026-10-11', '2026年10月11日'],
    ['2026-10-12', '2026年10月12日'],
    ['2026-01-05', '2026年1月5日'],
    ['2026-12-31', '2026年12月31日'],
  ])('%s を %s と表示する (0 埋めをしない)', (publishedDate, expected) => {
    expect(formatPublishedDate(publishedDate)).toBe(expected)
  })
})
