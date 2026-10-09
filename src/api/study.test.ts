import { describe, expect, it, vi } from 'vitest'
import { createStudyApi } from './study'
const book = { id: 1, slug: 'source:book', title: 'Book', author: null, description: null, module_code: 'SOURCE' }
const section = { id: 40, title: null, author: 'Author', chapter_from: 3, verse_from: 1, chapter_to: 4, verse_to: 2, book_osis_code: 'John' }
describe('study API', () => {
  it('reads full pagination metadata and safely encodes search without treating a book as a Bible translation', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: [book], total: 107 })))
    expect(await createStudyApi({ baseUrl: 'https://example.test/api', fetcher }).books('A & B', 20)).toEqual({ data: [book], total: 107 })
    const url = new URL(String(fetcher.mock.calls[0]?.[0])); expect(url.searchParams.get('q')).toBe('A & B'); expect(url.searchParams.get('offset')).toBe('20')
  })
  it('preserves source authors, multi-chapter ranges and section identity', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: { ...section, body: '<p>Text</p>' } })))
    expect(await createStudyApi({ baseUrl: 'https://example.test/api', fetcher }).article(1, 40)).toMatchObject(section)
    await expect(createStudyApi({ baseUrl: 'https://example.test/api', fetcher }).article(1, 41)).rejects.toMatchObject({ kind: 'invalid-response' })
  })
  it('rejects a contents page belonging to another book', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: { book, sections: [section], total: 938 } })))
    await expect(createStudyApi({ baseUrl: 'https://example.test/api', fetcher }).contents(2)).rejects.toMatchObject({ kind: 'invalid-response' })
  })
  it('loads chapter pages with explicit chosen modules rather than capped verse results', async () => {
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: { book: 'john', chapter: 3, entries: [], total: 199 } })))
    expect((await createStudyApi({ baseUrl: 'https://example.test/api', fetcher }).commentaries('john', 3, ['Z', 'A'], 10)).total).toBe(199)
    const url = new URL(String(fetcher.mock.calls[0]?.[0])); expect(url.pathname).toBe('/api/bible/books/john/chapters/3/commentaries'); expect(url.searchParams.get('modules')).toBe('A,Z')
  })
})
