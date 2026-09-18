import type { BibleApi } from '@/api/client'
import type { BibleChapter } from '@/api/contracts'
import { chapterKey, type ChapterRepository } from '@/offline/chapterRepository'

export interface ChapterService {
  download(translationCode: string, bookSlug: string, chapter: number): Promise<BibleChapter>
  readOffline(translationCode: string, bookSlug: string, chapter: number): Promise<BibleChapter | undefined>
  listStored(): Promise<import('@/offline/chapterRepository').StoredChapter[]>
  deleteStored(key: string): Promise<void>
}

export function createChapterService(api: BibleApi, repository: ChapterRepository): ChapterService {
  return {
    async download(translationCode, bookSlug, chapterNumber) {
      const data = await api.getChapter(translationCode, bookSlug, chapterNumber)
      await repository.put({
        key: chapterKey(translationCode, bookSlug, chapterNumber),
        savedAt: new Date().toISOString(),
        data,
      })
      return data
    },
    async readOffline(translationCode, bookSlug, chapterNumber) {
      const stored = await repository.get(chapterKey(translationCode, bookSlug, chapterNumber))
      return stored?.data
    },
    listStored() {
      return repository.list()
    },
    deleteStored(key) {
      return repository.delete(key)
    },
  }
}
