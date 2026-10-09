import 'fake-indexeddb/auto'
import { readFileSync } from 'node:fs'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, createBibleApi, type BibleApi } from '@/api/client'
import type { BibleBook, BibleChapter, TranslationSummary } from '@/api/contracts'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { offlineDatabaseName } from '@/offline/database'
import { chapterKey } from '@/offline/chapterRepository'
import { isInstalledPackage } from '@/offline/libraryRepository'
import families from '../../config/bible-language-groups.json'
import { installedBibles, loadBibleCatalog, matchingTranslations, searchLocalBible, translationGroup } from './bibleCatalog'
import { createOfflinePackageService } from './offlinePackageService'
import { createChapterService } from './chapterService'

const edition: TranslationSummary = { code: 'NEW', name: 'Новый перевод', short_name: 'NEW', language: { code: 'ru', name: 'Русский' }, canon_code: null, has_old_testament: true, has_new_testament: true, has_apocrypha: false, has_strong: false, is_default: false }
const book: BibleBook = { slug: 'genesis', name: 'Бытие', short_name: 'Быт.', chapters_count: 3, order: 1 }
function chapter(number: number): BibleChapter {
  return { translation: edition, book, chapter: { number, verses_count: 1 }, verses: [{ id: number, number: 1, osis_ref: `Gen.${number}.1`, text: 'В начале сотворил Бог', plain_text: 'В начале сотворил Бог', has_strong_markup: false }] }
}
function api() { return { getBooks: vi.fn(async () => [book]), getChapter: vi.fn(async (_code: string, _slug: string, number: number) => chapter(number)) } as unknown as BibleApi }
beforeEach(async () => {
  await new Promise<void>((resolve, reject) => { const request = indexedDB.deleteDatabase(offlineDatabaseName); request.onsuccess = () => resolve(); request.onerror = () => reject(request.error) })
})

describe('installed Bibles and public catalogue', () => {
  it('requests all available public editions, not only default editions or private modules', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: [edition] })))
    expect(await createBibleApi({ baseUrl: 'https://example.test/api', fetcher }).getTranslations()).toEqual([edition])
    expect(String(fetcher.mock.calls[0]?.[0])).toBe('https://example.test/api/translations?catalog=available')
  })
  it('filters hundreds of editions by family, language and normalized search without hiding new languages', () => {
    const catalog = Array.from({ length: 400 }, (_, index) => ({ ...edition, code: `BOOK${index}` }))
    catalog.push({ ...edition, code: 'PL', name: 'Biblia gdańska', language: { code: 'pl', name: 'Polski' } })
    expect(matchingTranslations(catalog, 'GDANSKA polski', 'slavic', 'pl').map(item => item.code)).toEqual(['PL'])
    expect(translationGroup({ ...edition, language: { code: 'unknown-new-code', name: 'New language' } })).toBe('other')
    expect(matchingTranslations([{ ...edition, language: { code: 'unknown-new-code', name: 'New language' } }], 'new language', 'other')).toHaveLength(1)
  })
  it('keeps web language families identical to the native classification', () => {
    const native: Record<string, string> = {}
    const exact = readFileSync('mobile/androidApp/src/main/kotlin/com/bibledesktop/myapp/ui/bible/CatalogLanguageFamilies.kt', 'utf8')
    for (const match of exact.matchAll(/"([^"]+)" to "([^"]+)"/g)) native[match[1]!] = match[2]!
    const manual = readFileSync('mobile/androidApp/src/main/kotlin/com/bibledesktop/myapp/ui/bible/TranslationCatalog.kt', 'utf8')
    for (const match of manual.matchAll(/group\("([^"]+)", "([^"]+)"\)/g)) for (const code of match[2]!.split(' ')) native[code] = match[1]!
    expect(families).toEqual(native)
  })
  it('uses a saved catalogue offline but never conceals authorization errors', async () => {
    const source = { getTranslations: vi.fn(async () => [edition]) } as unknown as BibleApi
    expect(await loadBibleCatalog(source)).toEqual([edition])
    vi.mocked(source.getTranslations).mockRejectedValue(new ApiError('offline', 'offline'))
    expect(await loadBibleCatalog(source)).toEqual([edition])
    vi.mocked(source.getTranslations).mockRejectedValue(new ApiError('http', 'forbidden', 403))
    await expect(loadBibleCatalog(source)).rejects.toMatchObject({ status: 403 })
  })
  it('continues a cancelled package with committed chapters, then reads and searches after repository recreation without API', async () => {
    const source = api(), chapters = createIndexedDbChapterRepository(), library = createIndexedDbLibraryRepository()
    const controller = new AbortController()
    await expect(createOfflinePackageService(source, chapters, library).download(edition, () => controller.abort(), controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
    expect(await installedBibles(library, chapters)).toHaveLength(0)
    expect((await library.getPackage(edition.code))?.chapterCount).toBe(1)
    const ready = await createOfflinePackageService(source, chapters, library).download(edition, () => {})
    expect(ready.complete).toBe(true)
    expect(source.getChapter).toHaveBeenCalledTimes(3)
    const reopened = createIndexedDbChapterRepository()
    const offline = { getChapter: vi.fn(() => { throw new Error('No network allowed') }) } as unknown as BibleApi
    expect((await createChapterService(offline, reopened).readOffline('NEW', 'genesis', 2))?.verses[0]?.plain_text).toContain('Бог')
    expect(await installedBibles(createIndexedDbLibraryRepository(), reopened)).toHaveLength(1)
    expect(await searchLocalBible(reopened, 'NEW', 'сотворил бог')).toHaveLength(3)
    expect(offline.getChapter).not.toHaveBeenCalled()
  })
  it('preserves missing chapters and merged/blank verses explicitly without reporting a complete text', async () => {
    const source = api()
    vi.mocked(source.getChapter).mockImplementation(async (_code, _slug, number) => {
      const value = chapter(number)
      if (number === 2) { value.verses = []; value.chapter.verses_count = 0 }
      if (number === 3) { value.verses.push({ ...value.verses[0]!, id: 40, number: 2, osis_ref: 'Gen.3.2', text: '', plain_text: '' }); value.chapter.verses_count = 2 }
      return value
    })
    const chapters = createIndexedDbChapterRepository(), library = createIndexedDbLibraryRepository()
    const result = await createOfflinePackageService(source, chapters, library).download(edition, () => {})
    expect(result).toMatchObject({ finished: true, complete: false, chapterCount: 2, totalChapters: 3, unavailable: ['Бытие 2'], missingVerses: ['Gen.3.2'] })
    expect(isInstalledPackage(result)).toBe(true)
    expect((await chapters.get(chapterKey('NEW', 'genesis', 3)))?.data.verses[1]?.plain_text).toBe('')
  })
  it('rejects wrong editions before committing chapter text', async () => {
    const source = api(), chapters = createIndexedDbChapterRepository(), library = createIndexedDbLibraryRepository()
    vi.mocked(source.getChapter).mockResolvedValue({ ...chapter(1), translation: { ...edition, code: 'WRONG' } })
    await expect(createOfflinePackageService(source, chapters, library).download(edition, () => {})).rejects.toThrow('Invalid chapter')
    expect(await chapters.list()).toHaveLength(0)
    expect(isInstalledPackage((await library.getPackage('NEW'))!)).toBe(false)
  })
  it('keeps legacy full packages readable without deleting their chapters', async () => {
    const chapters = createIndexedDbChapterRepository(), library = createIndexedDbLibraryRepository()
    await chapters.put({ key: 'NEW:genesis:1', savedAt: 'old', data: chapter(1) })
    await library.putPackage({ key: 'package:NEW', translationCode: 'NEW', translationName: 'Old', catalogVersion: 'old', chapterCount: 1, approximateBytes: 100, downloadedAt: 'old' })
    expect((await installedBibles(library, chapters))[0]?.translation.code).toBe('NEW')
    expect(await chapters.list()).toHaveLength(1)
  })
  it('does not mark a package finished when storing chapter text fails', async () => {
    const chapters = createIndexedDbChapterRepository(), library = createIndexedDbLibraryRepository()
    vi.spyOn(chapters, 'put').mockRejectedValue(new DOMException('Full', 'QuotaExceededError'))
    await expect(createOfflinePackageService(api(), chapters, library).download(edition, () => {})).rejects.toMatchObject({ name: 'QuotaExceededError' })
    expect((await library.getPackage('NEW'))?.finished).toBe(false)
  })
})
