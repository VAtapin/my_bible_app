import type { BibleApi } from '@/api/client'
import type { BibleChapter } from '@/api/contracts'
import { chapterKey, type ChapterRepository } from '@/offline/chapterRepository'
import { validChapter } from './chapterValidation'

export interface ChapterService {
  download(translationCode: string, bookSlug: string, chapter: number): Promise<BibleChapter>
  readOnline?(translationCode: string, bookSlug: string, chapter: number): Promise<BibleChapter>
  readOffline(translationCode: string, bookSlug: string, chapter: number): Promise<BibleChapter | undefined>
  listStored(): Promise<import('@/offline/chapterRepository').StoredChapter[]>
  deleteStored(key: string): Promise<void>
}

export function createChapterService(api: BibleApi, repository: ChapterRepository): ChapterService {
  async function load(translationCode: string, bookSlug: string, chapterNumber: number, requireCache: boolean): Promise<BibleChapter> {
    const data = await api.getChapter(translationCode, bookSlug, chapterNumber)
    if (!validChapter(data, translationCode, bookSlug, chapterNumber)) throw new Error('Invalid chapter in Bible package')
    try {
      await repository.put({ key: chapterKey(translationCode, bookSlug, chapterNumber), savedAt: new Date().toISOString(), data })
    } catch (error) {
      if (requireCache) throw error
      // Web reading is online, not an installation promise. Cache failure must not hide a valid API text.
    }
    return data
  }
  return {
    download: (code, slug, number) => load(code, slug, number, true),
    readOnline: (code, slug, number) => load(code, slug, number, false),
    async readOffline(translationCode, bookSlug, chapterNumber) {
      const stored = await repository.get(chapterKey(translationCode, bookSlug, chapterNumber))
      return stored && validChapter(stored.data, translationCode, bookSlug, chapterNumber) ? stored.data : undefined
    },
    listStored() {
      return repository.list()
    },
    deleteStored(key) {
      return repository.delete(key)
    },
  }
}
