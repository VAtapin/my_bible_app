import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, type BibleApi } from '@/api/client'
import type { TranslationSummary } from '@/api/contracts'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { offlineDatabaseName } from '@/offline/database'
import { enabledWebBibles, saveEnabledWebBibles, synodalCode, readWebChapter, webBibleBooks } from './webBibleLibrary'
import { createChapterService } from './chapterService'
import type { ChapterRepository } from '@/offline/chapterRepository'

const edition: TranslationSummary = { code: synodalCode, name: 'Синодальная Библия', short_name: 'RST-Strong', language: { code: 'ru', name: 'Русский' }, canon_code: null, has_old_testament: true, has_new_testament: true, has_apocrypha: false, has_strong: true, is_default: true }
const second = { ...edition, code: 'SECOND', name: 'Second' }
const book = { slug: 'genesis', name: 'Бытие', short_name: 'Быт.', order: 1, chapters_count: 50, canonical_book: { osis_code: 'Gen' } }
const chapter = { translation: edition, book, chapter: { number: 1, verses_count: 1 }, verses: [{ id: 1, number: 1, osis_ref: 'Gen.1.1', plain_text: 'В начале', text: 'В начале', has_strong_markup: false }] }
beforeEach(async () => {
  await new Promise<void>((resolve, reject) => { const request = indexedDB.deleteDatabase(offlineDatabaseName); request.onsuccess = () => resolve(); request.onerror = () => reject(request.error) })
})
describe('web enabling and online Bible reads', () => {
  it('does not require saving a chapter to read it online', async () => {
    const repository = { put: vi.fn(async () => { throw new DOMException('Quota exceeded', 'QuotaExceededError') }) } as unknown as ChapterRepository
    const api = { getChapter: vi.fn(async () => chapter) } as unknown as BibleApi
    const service = createChapterService(api, repository)
    expect(await readWebChapter(service, synodalCode, book.slug, 1)).toEqual(chapter)
    await expect(service.download(synodalCode, book.slug, 1)).rejects.toMatchObject({ name: 'QuotaExceededError' })
  })
  it('enables Synodal by default without downloading any books or chapters', async () => {
    const api = { getTranslations: vi.fn(async () => [second, edition]), getBooks: vi.fn(), getChapter: vi.fn() } as unknown as BibleApi
    expect(await enabledWebBibles(api)).toEqual([edition])
    expect(api.getBooks).not.toHaveBeenCalled(); expect(api.getChapter).not.toHaveBeenCalled()
    expect(await createIndexedDbLibraryRepository().listPackages()).toEqual([])
  })
  it('persists enabling/disabling, retains cached texts and prevents removing the last edition', async () => {
    await saveEnabledWebBibles([edition, second])
    const api = { getTranslations: vi.fn() } as unknown as BibleApi
    expect(await enabledWebBibles(api)).toEqual([edition, second])
    await saveEnabledWebBibles([second])
    expect(await enabledWebBibles(api)).toEqual([second])
    await expect(saveEnabledWebBibles([])).rejects.toThrow()
    expect(await enabledWebBibles(api)).toEqual([second])
    expect(api.getTranslations).not.toHaveBeenCalled()
  })
  it('reads without a full package, refreshes online, and only falls back on a connection failure', async () => {
    const api = { getChapter: vi.fn(async () => chapter) } as unknown as BibleApi
    const service = createChapterService(api, createIndexedDbChapterRepository())
    expect(await readWebChapter(service, synodalCode, book.slug, 1)).toEqual(chapter)
    expect(await createIndexedDbLibraryRepository().listPackages()).toEqual([])
    vi.mocked(api.getChapter).mockRejectedValue(new ApiError('offline', 'offline'))
    expect(await readWebChapter(service, synodalCode, book.slug, 1)).toEqual(chapter)
    vi.mocked(api.getChapter).mockRejectedValue(new ApiError('http', 'forbidden', 403))
    await expect(readWebChapter(service, synodalCode, book.slug, 1)).rejects.toMatchObject({ status: 403 })
  })
  it('gets book metadata online and can reuse it offline without a full installation', async () => {
    const api = { getBooks: vi.fn(async () => [book]) } as unknown as BibleApi
    expect(await webBibleBooks(api, synodalCode)).toEqual([book])
    vi.mocked(api.getBooks).mockRejectedValue(new ApiError('timeout', 'timeout'))
    expect(await webBibleBooks(api, synodalCode)).toEqual([book])
    vi.mocked(api.getBooks).mockRejectedValue(new ApiError('http', 'rate limit', 429))
    await expect(webBibleBooks(api, synodalCode)).rejects.toMatchObject({ status: 429 })
  })
})
