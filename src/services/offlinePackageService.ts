import type { BibleApi } from '@/api/client'
import type { BibleBook, TranslationSummary } from '@/api/contracts'
import { ApiError } from '@/api/client'
import { chapterKey } from '@/offline/chapterRepository'
import type { ChapterRepository } from '@/offline/chapterRepository'
import type { LibraryRepository, OfflinePackage } from '@/offline/libraryRepository'
import { createChapterService } from './chapterService'
import { validChapter } from './chapterValidation'

export interface PackageProgress {
  current: number
  total: number
  bookName: string
  chapter: number
}

export interface PackageStatus {
  stored?: OfflinePackage
  catalogVersion: string
  updateAvailable: boolean
  totalChapters: number
}

export function createOfflinePackageService(
  api: BibleApi,
  chapters: ChapterRepository,
  library: LibraryRepository,
) {
  const chapterService = createChapterService(api, chapters)

  const inspect = async (translationCode: string): Promise<PackageStatus> => {
    const books = await api.getBooks(translationCode)
    const catalogVersion = createCatalogVersion(books)
    const stored = await library.getPackage(translationCode)
    return {
      stored,
      catalogVersion,
      updateAvailable: Boolean(stored && stored.catalogVersion !== catalogVersion),
      totalChapters: countChapters(books),
    }
  }

  return {
    inspect,
    async download(
      translation: TranslationSummary,
      onProgress: (progress: PackageProgress) => void,
      signal?: AbortSignal,
    ): Promise<OfflinePackage> {
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      const books = await api.getBooks(translation.code)
      if (!books.length || books.some(book => !book.slug || !Number.isInteger(book.chapters_count) || book.chapters_count <= 0)
          || new Set(books.map(book => book.slug)).size !== books.length) throw new Error('Invalid Bible book catalog')
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      const total = countChapters(books)
      let current = 0
      let approximateBytes = 0
      const unavailable: string[] = []
      const missingVerses: string[] = []
      const value: OfflinePackage = {
        key: `package:${translation.code}`, translationCode: translation.code, translationName: translation.name,
        translation, books, totalChapters: total, catalogVersion: createCatalogVersion(books), chapterCount: 0,
        approximateBytes: 0, downloadedAt: new Date().toISOString(), finished: false, complete: false, unavailable, missingVerses,
      }
      const checkpoint = async () => {
        value.chapterCount = current; value.approximateBytes = approximateBytes
        await library.putPackage({ ...value, unavailable: [...unavailable], missingVerses: [...missingVerses] })
      }
      await checkpoint()

      for (const book of books) {
        for (let chapterNumber = 1; chapterNumber <= book.chapters_count; chapterNumber += 1) {
          if (signal?.aborted) {
            throw new DOMException('Загрузка остановлена.', 'AbortError')
          }
          let chapter = (await chapters.get(chapterKey(translation.code, book.slug, chapterNumber)))?.data
          if (!chapter || !validChapter(chapter, translation.code, book.slug, chapterNumber)
              || !chapter.verses.length || chapter.verses.some(verse => !verse.plain_text.trim())) {
            await pause(350, signal)
            for (let attempt = 0; ; attempt++) {
              try { chapter = await chapterService.download(translation.code, book.slug, chapterNumber); break }
              catch (error) {
                if (!(error instanceof ApiError) || ![429, 503, 502, 500, 504].includes(error.status ?? 0) || attempt >= 3) throw error
                await pause(Math.max(1000, error.retryAfterMs ?? 30_000), signal)
              }
            }
          }
          if (!validChapter(chapter, translation.code, book.slug, chapterNumber)) throw new Error('Invalid chapter in Bible package')
          if (chapter.verses.every(verse => !verse.plain_text.trim())) {
            unavailable.push(`${book.name} ${chapterNumber}`)
            await checkpoint()
            onProgress({ current, total, bookName: book.name, chapter: chapterNumber })
            continue
          }
          missingVerses.push(...chapter.verses.filter(verse => !verse.plain_text.trim()).map(verse => verse.osis_ref))
          approximateBytes += new Blob([JSON.stringify(chapter)]).size
          current += 1
          await checkpoint()
          onProgress({ current, total, bookName: book.name, chapter: chapterNumber })
        }
      }

      value.finished = true
      value.complete = current === total && missingVerses.length === 0
      await checkpoint()
      return value
    },
  }
}

function pause(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve() }, ms)
    if (signal?.aborted) abort()
    else signal?.addEventListener('abort', abort, { once: true })
  })
}

export function createCatalogVersion(books: BibleBook[]): string {
  return `catalog-v1:${books.map((book) => `${book.slug}:${book.chapters_count}`).join('|')}`
}

function countChapters(books: BibleBook[]): number {
  return books.reduce((total, book) => total + book.chapters_count, 0)
}
