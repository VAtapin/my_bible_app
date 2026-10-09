import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { createStudyService, overlaps, type StudyCache } from './studyService'
import type { StudyApi } from '@/api/study'
describe('study material ranges and offline access', () => {
  const range = { chapter_from: 3, verse_from: 16, chapter_to: 4, verse_to: 2 }
  it('includes range intersections and introductions exactly, without equating adjacent verses', () => {
    expect(overlaps(range, 3, 15, 16)).toBe(true); expect(overlaps(range, 3, 1, 15)).toBe(false)
    expect(overlaps(range, 4, 2, 5)).toBe(true); expect(overlaps(range, 4, 3, 5)).toBe(false)
    expect(overlaps({ ...range, chapter_from: 0 }, 2, 1, 1)).toBe(true)
    expect(overlaps({ chapter_from: 3, verse_from: 0, chapter_to: null, verse_to: null }, 3, 9, 9)).toBe(true)
    expect(overlaps({ chapter_from: 3, verse_from: 16, chapter_to: null, verse_to: null }, 3, 17, 17)).toBe(false)
  })
  it.each(['offline', 'timeout'] as const)('allows cached pages only on %s', async kind => {
    const value = { data: [], total: 0 }; const cache: StudyCache = { read: async <T>() => value as T, write: vi.fn() }
    const api = { books: vi.fn().mockRejectedValue(new ApiError(kind, 'network')) } as unknown as StudyApi
    expect(await createStudyService(api, cache).books()).toEqual(value)
  })
  it.each([403, 429, 500])('does not mask HTTP %s with cached materials', async status => {
    const cache: StudyCache = { read: vi.fn(), write: vi.fn() }
    const api = { books: vi.fn().mockRejectedValue(new ApiError('http', 'http', status)) } as unknown as StudyApi
    await expect(createStudyService(api, cache).books()).rejects.toMatchObject({ status }); expect(cache.read).not.toHaveBeenCalled()
  })
  it('keeps live reading available on cache quota failure without promising installation', async () => {
    const cache: StudyCache = { read: vi.fn(), write: vi.fn().mockRejectedValue(new Error('quota')) }
    const api = { books: vi.fn().mockResolvedValue({ data: [], total: 0 }) } as unknown as StudyApi
    expect(await createStudyService(api, cache).books()).toEqual({ data: [], total: 0 })
  })
})
