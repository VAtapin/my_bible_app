import { describe, expect, it, vi } from 'vitest'
import type { BibleChapter } from '@/api/contracts'
import type { BibleApi } from '@/api/client'
import type { ChapterService } from './chapterService'
import { compareVerses, loadComparison } from './bibleComparison'

function chapter(code: string, slug: string, references: string[]): BibleChapter {
  return {
    translation: { code, name: code, short_name: null, language: { code: 'ru', name: 'Русский' } },
    book: { slug, name: slug, short_name: null, chapters_count: 21 }, chapter: { number: 3, verses_count: references.length },
    verses: references.map((osis_ref, index) => ({ id: index, number: Number(osis_ref.split('.').at(-1)), osis_ref, text: osis_ref, plain_text: osis_ref, has_strong_markup: false })),
  }
}
const primary = chapter('A', 'ioann', ['John.3.1', 'John.3.3'])
const secondary = chapter('B', 'john', ['John.3.1', 'John.3.2'])
function fixtures() {
  const api = { getBooks: vi.fn(async () => [{ slug: 'john', chapters_count: 21, canonical_book: { osis_code: 'John' } }]) } as unknown as BibleApi
  const service = { listStored: vi.fn(async () => []), download: vi.fn(async () => secondary) } as unknown as ChapterService
  return { api, service }
}
describe('parallel reading', () => {
  it('matches exact references and includes gaps on both sides', () => {
    const rows = compareVerses(primary, secondary)
    expect(rows.map(row => row.reference)).toEqual(['John.3.1', 'John.3.2', 'John.3.3'])
    expect(rows[1]?.primary).toBeUndefined()
    expect(rows[2]?.secondary).toBeUndefined()
  })
  it('rejects duplicate verse identities', () => {
    expect(() => compareVerses(primary, chapter('B', 'john', ['John.3.1', 'John.3.1']))).toThrow()
  })
  it('opens a canonical book with a different slug', async () => {
    const { api, service } = fixtures()
    expect(await loadComparison(primary, 'B', api, service)).toEqual(secondary)
    expect(service.download).toHaveBeenCalledWith('B', 'john', 3)
  })
  it('never guesses from matching slugs when canonical metadata differs', async () => {
    const { api, service } = fixtures()
    vi.mocked(api.getBooks).mockResolvedValue([{ slug: 'ioann', name: 'Иоанна', short_name: null, order: 1, chapters_count: 21, canonical_book: { osis_code: 'Gen' } }])
    await expect(loadComparison(primary, 'B', api, service)).rejects.toThrow()
    expect(service.download).not.toHaveBeenCalled()
  })
  it('reopens only the matching saved chapter without a catalog connection', async () => {
    const { api, service } = fixtures()
    vi.mocked(api.getBooks).mockRejectedValue(new Error('offline'))
    vi.mocked(service.listStored).mockResolvedValue([{ key: 'B:john:3', savedAt: '', data: secondary }])
    expect(await loadComparison(primary, 'B', api, service)).toEqual(secondary)
  })
  it('rejects a returned chapter from a different canonical book', async () => {
    const { api, service } = fixtures()
    vi.mocked(service.download).mockResolvedValue(chapter('B', 'john', ['Gen.3.1']))
    await expect(loadComparison(primary, 'B', api, service)).rejects.toThrow()
  })
})
