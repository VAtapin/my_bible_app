import { describe, expect, it, vi } from 'vitest'
import type { BibleApi } from '@/api/client'
import type { BibleChapter, TranslationSummary } from '@/api/contracts'
import type { ChapterRepository, StoredChapter } from '@/offline/chapterRepository'
import type { LibraryRepository, OfflinePackage, ReadingLocation, Bookmark } from '@/offline/libraryRepository'
import { createCatalogVersion, createOfflinePackageService } from './offlinePackageService'

const translation: TranslationSummary = {
  code: 'RST', name: 'Синодальный', short_name: 'RST', language: { code: 'ru', name: 'Русский' },
  canon_code: 'bible', has_old_testament: true, has_new_testament: true,
  has_apocrypha: false, has_strong: false, is_default: true,
}

function chapter(number: number): BibleChapter {
  return {
    translation: { code: 'RST', name: 'Синодальный', short_name: 'RST', language: { code: 'ru', name: 'Русский' } },
    book: { slug: 'genesis', name: 'Бытие', short_name: 'Быт.', chapters_count: 2 },
    chapter: { number, verses_count: 1 },
    verses: [{ id: number, number: 1, osis_ref: `Gen.${number}.1`, text: 'Текст', plain_text: 'Текст', has_strong_markup: false }],
  }
}

describe('offline package service', () => {
  it('marks a package complete only after every chapter is stored', async () => {
    const storedChapters: StoredChapter[] = []
    let storedPackage: OfflinePackage | undefined
    const chapters: ChapterRepository = {
      get: vi.fn(),
      put: vi.fn(async (value) => { storedChapters.push(value) }),
      list: vi.fn(async () => storedChapters),
      delete: vi.fn(),
    }
    const library: LibraryRepository = {
      getReadingLocation: vi.fn(async () => undefined),
      saveReadingLocation: vi.fn(async (_value: ReadingLocation) => undefined),
      listBookmarks: vi.fn(async () => [] as Bookmark[]),
      putBookmark: vi.fn(), deleteBookmark: vi.fn(),
      getPackage: vi.fn(async () => storedPackage),
      listPackages: vi.fn(async () => storedPackage ? [storedPackage] : []),
      putPackage: vi.fn(async (value) => { storedPackage = value }),
      deletePackage: vi.fn(),
    }
    const api = {
      getTranslations: vi.fn(),
      getBooks: vi.fn(async () => [{ slug: 'genesis', name: 'Бытие', short_name: null, order: 1, chapters_count: 2 }]),
      getChapter: vi.fn(async (_translation: string, _book: string, number: number) => chapter(number)),
    } satisfies BibleApi
    const progress = vi.fn()

    const result = await createOfflinePackageService(api, chapters, library).download(translation, progress)

    expect(storedChapters).toHaveLength(2)
    expect(result.chapterCount).toBe(2)
    expect(library.putPackage).toHaveBeenCalledTimes(1)
    expect(progress).toHaveBeenLastCalledWith(expect.objectContaining({ current: 2, total: 2 }))
  })

  it('builds a stable catalog version from book slugs and chapter counts', () => {
    expect(createCatalogVersion([
      { slug: 'genesis', name: 'Бытие', short_name: null, order: 1, chapters_count: 50 },
      { slug: 'exodus', name: 'Исход', short_name: null, order: 2, chapters_count: 40 },
    ])).toBe('catalog-v1:genesis:50|exodus:40')
  })
})
