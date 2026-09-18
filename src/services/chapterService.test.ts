import { describe, expect, it, vi } from 'vitest'
import type { BibleApi } from '@/api/client'
import type { BibleChapter } from '@/api/contracts'
import type { ChapterRepository, StoredChapter } from '@/offline/chapterRepository'
import { createChapterService } from './chapterService'

const chapter: BibleChapter = {
  translation: { code: 'L1_RST', name: 'Синодальный', short_name: 'RST', language: { code: 'ru', name: 'Русский' } },
  book: { slug: 'genesis', name: 'Бытие', short_name: 'Быт.', chapters_count: 50 },
  chapter: { number: 1, verses_count: 1 },
  verses: [{ id: 1, number: 1, osis_ref: 'Gen.1.1', text: 'В начале', plain_text: 'В начале', has_strong_markup: false }],
}

describe('chapter service', () => {
  it('persists a downloaded chapter and returns it offline', async () => {
    let stored: StoredChapter | undefined
    const repository: ChapterRepository = {
      get: vi.fn(async () => stored),
      put: vi.fn(async (value) => { stored = value }),
    }
    const api = {
      getChapter: vi.fn(async () => chapter),
      getBooks: vi.fn(),
      getTranslations: vi.fn(),
    } satisfies BibleApi
    const service = createChapterService(api, repository)

    await service.download('L1_RST', 'genesis', 1)
    const offlineChapter = await service.readOffline('L1_RST', 'genesis', 1)

    expect(repository.put).toHaveBeenCalledWith(expect.objectContaining({ key: 'L1_RST:genesis:1' }))
    expect(offlineChapter).toEqual(chapter)
  })
})
