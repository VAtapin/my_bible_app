import { describe, expect, it } from 'vitest'
import { dictionaryMediaUrl, isDictionaryArticle } from './dictionaries'
describe('dictionary contract', () => {
  it('accepts source links, media and canonical ranges', () => {
    expect(isDictionaryArticle({ id: 7, key: 'a'.repeat(40), topic: 'Иерусалим', body: '<p>Текст</p>', media: [{ id: 2, fragment_id: 'map', url: '/api/dictionaries/BMaps/media/2' }], links: [{ key: 'b'.repeat(40), topic: 'Иудея', label: 'Иудея' }], references: [{ book_slug: 'john', chapter_number: 3, verse_from: 1, verse_to: 5 }] })).toBe(true)
  })
  it('rejects malformed identifiers and range types', () => {
    expect(isDictionaryArticle({ id: 7, key: '../secret', topic: 'Title', body: 'Text', media: [], links: [], references: [] })).toBe(false)
    expect(isDictionaryArticle({ id: 7, key: 'a'.repeat(40), topic: 'Title', body: 'Text', media: [], links: [], references: [{ book_slug: 'john', chapter_number: '3', verse_from: 1, verse_to: 5 }] })).toBe(false)
  })
  it('only resolves published images belonging to this source', () => {
    expect(dictionaryMediaUrl('https://bible-desktop.com/api', 'BMaps', 2, '/api/dictionaries/BMaps/media/2')).toBe('https://bible-desktop.com/api/dictionaries/BMaps/media/2')
    for (const url of ['https://evil.test/api/dictionaries/BMaps/media/2', '/api/dictionaries/Other/media/2', '/api/dictionaries/BMaps/media/2?token=secret', 'javascript:alert(1)']) expect(() => dictionaryMediaUrl('https://bible-desktop.com/api', 'BMaps', 2, url)).toThrow()
  })
  it('separates restored images in HTTP caches without accepting untrusted URL parameters',()=>{
    expect(dictionaryMediaUrl('https://bible-desktop.com/api','BMaps',2,'/api/dictionaries/BMaps/media/2','hash-media-v2')).toBe('https://bible-desktop.com/api/dictionaries/BMaps/media/2?v=hash-media-v2')
    expect(()=>dictionaryMediaUrl('https://bible-desktop.com/api','BMaps',2,'/api/dictionaries/BMaps/media/2?token=secret','hash-media-v2')).toThrow()
  })
})
