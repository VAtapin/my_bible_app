import type { BibleApi } from '@/api/client'
import type { BibleBook, TranslationSummary } from '@/api/contracts'
import type { ChapterRepository } from '@/offline/chapterRepository'
import type { LibraryRepository, OfflinePackage } from '@/offline/libraryRepository'
import { createChapterService } from './chapterService'

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
      const books = await api.getBooks(translation.code)
      const total = countChapters(books)
      let current = 0
      let approximateBytes = 0

      for (const book of books) {
        for (let chapterNumber = 1; chapterNumber <= book.chapters_count; chapterNumber += 1) {
          if (signal?.aborted) {
            throw new DOMException('Загрузка остановлена.', 'AbortError')
          }
          const chapter = await chapterService.download(translation.code, book.slug, chapterNumber)
          approximateBytes += new Blob([JSON.stringify(chapter)]).size
          current += 1
          onProgress({ current, total, bookName: book.name, chapter: chapterNumber })
        }
      }

      const value: OfflinePackage = {
        key: `package:${translation.code}`,
        translationCode: translation.code,
        translationName: translation.name,
        catalogVersion: createCatalogVersion(books),
        chapterCount: current,
        approximateBytes,
        downloadedAt: new Date().toISOString(),
      }
      await library.putPackage(value)
      return value
    },
  }
}

export function createCatalogVersion(books: BibleBook[]): string {
  return `catalog-v1:${books.map((book) => `${book.slug}:${book.chapters_count}`).join('|')}`
}

function countChapters(books: BibleBook[]): number {
  return books.reduce((total, book) => total + book.chapters_count, 0)
}
