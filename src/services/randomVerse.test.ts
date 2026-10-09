import { describe, expect, it, vi } from 'vitest'
import type { BibleChapter } from '@/api/contracts'
import type { ChapterService } from './chapterService'
import { chooseVerse, loadRandomVerse } from './randomVerse'
import { loadGospelExcerpt } from './dailyReading'

const chapter: BibleChapter = {
  translation: { code: 'RST', name: 'Синодальный', short_name: 'RST', language: { code: 'ru', name: 'Русский' } },
  book: { slug: 'john', name: 'Иоанн', short_name: 'Ин.', chapters_count: 21 },
  chapter: { number: 1, verses_count: 2 },
  verses: [1, 2].map((number) => ({ id: number, number, osis_ref: `John.1.${number}`, text: `Текст ${number}`, plain_text: `Текст ${number}`, has_strong_markup: false })),
}
function service(): ChapterService {
  return { readOffline: vi.fn(async () => undefined), download: vi.fn(async () => chapter), listStored: vi.fn(async () => []), deleteStored: vi.fn() }
}
describe('real daily verses', () => {
  it('selects an actual verse with its reference and internal reader link', () => {
    expect(chooseVerse(chapter, () => .9)).toEqual({ text: 'Текст 2', reference: 'Ин. 1:2', route: { path: '/reader', query: { translation: 'RST', book: 'john', chapter: '1', verse: '2' } } })
    expect(chooseVerse({ ...chapter, verses: [] })).toBeUndefined()
    expect(chooseVerse({ ...chapter, book: { ...chapter.book, short_name: 'Ин. Иоан. John Jn' } }, () => 0)?.reference).toBe('Ин. 1:1')
  })
  it('uses cached content without downloading it again', async () => {
    const api = service()
    api.readOffline = vi.fn(async () => chapter)
    expect((await loadRandomVerse(api, 'RST', () => .3))?.text).toBe('Текст 1')
    expect(api.download).not.toHaveBeenCalled()
  })
  it('falls back only to the requested translation, never to a fabricated quotation', async () => {
    const api = service()
    api.download = vi.fn(async () => { throw new Error('offline') })
    api.listStored = vi.fn(async () => [{ key: 'RST:john:1', savedAt: '', data: chapter }])
    expect((await loadRandomVerse(api, 'RST', () => 0))?.text).toBe('Текст 1')
    expect(await loadRandomVerse(api, 'DE', () => 0)).toBeUndefined()
  })
  it('shows the appointed Gospel verse, not a random verse labelled as a daily reading', async () => {
    const reading = { id: 'gospel', type: 'gospel', title: '', date_rule_type: '', display_ref: 'Ин. 1:2', passage_ref: 'John.1.2', reading: { schemaVersion: 1, parseStatus: 'ok', passages: [{ book: 'John', start: { chapter: 1, verse: 2 }, end: { chapter: 1, verse: 2 } }] } }
    const local = service()
    local.readOffline = vi.fn(async () => chapter)
    expect((await loadGospelExcerpt(local, [reading], 'RST'))?.text).toBe('Текст 2')
    expect(local.download).not.toHaveBeenCalled()
    expect(await loadGospelExcerpt(service(), [reading], 'RST')).toBeUndefined()
    expect(await loadGospelExcerpt(service(), [], 'RST')).toBeUndefined()
  })
})
