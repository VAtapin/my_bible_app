import { describe, expect, it } from 'vitest'
import { localDateKey, previousLocalDateKey } from './date'

describe('local date keys', () => {
  it('uses the local calendar date instead of UTC', () => {
    expect(localDateKey(new Date(2026, 0, 2, 23, 30))).toBe('2026-01-02')
  })

  it('crosses month and year boundaries safely', () => {
    expect(previousLocalDateKey(new Date(2026, 0, 1, 12))).toBe('2025-12-31')
  })
})
