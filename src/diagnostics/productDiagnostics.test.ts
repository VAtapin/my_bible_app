import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearProductDiagnostics,
  loadProductDiagnostics,
  recordProductMetric,
  recordSanitizedError,
} from './productDiagnostics'

class MemoryStorage implements Storage {
  private values = new Map<string, string>()
  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(key) ?? null }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  removeItem(key: string) { this.values.delete(key) }
  setItem(key: string, value: string) { this.values.set(key, value) }
}

beforeEach(() => {
  vi.stubGlobal('localStorage', new MemoryStorage())
})

describe('privacy-safe product diagnostics', () => {
  it('stores only aggregate counters and timestamps', () => {
    const now = new Date('2026-09-18T12:00:00Z')
    recordProductMetric('constructor_completed', now)
    recordProductMetric('constructor_completed', now)
    recordSanitizedError('offline_download', now)

    expect(loadProductDiagnostics()).toEqual({
      version: 1,
      metrics: { constructor_completed: { count: 2, lastAt: now.toISOString() } },
      errors: { offline_download: { count: 1, lastAt: now.toISOString() } },
    })
    expect(JSON.stringify(loadProductDiagnostics())).not.toMatch(/prayer|query|message|stack/i)
  })

  it('ignores malformed or unknown diagnostic fields', () => {
    localStorage.setItem('bible-desktop:product-diagnostics:v1', JSON.stringify({
      version: 1,
      metrics: { search_query: { count: 1, lastAt: 'now' } },
      errors: {},
    }))
    expect(loadProductDiagnostics()).toEqual({ version: 1, metrics: {}, errors: {} })

    recordProductMetric('app_opened')
    clearProductDiagnostics()
    expect(loadProductDiagnostics()).toEqual({ version: 1, metrics: {}, errors: {} })
  })
})
