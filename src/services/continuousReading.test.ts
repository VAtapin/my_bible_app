import { describe, expect, it } from 'vitest'
import type { BibleChapter } from '@/api/contracts'
import { validateContinuation } from './continuousReading'
const chapter = { translation: { code: 'SOURCE' }, book: { slug: 'acts' }, chapter: { number: 5 }, verses: [{ number: 1, osis_ref: 'Acts.5.1' }] } as BibleChapter
describe('continuous reading boundaries', () => {
  const next = { ...chapter, chapter: { ...chapter.chapter, number: 6 }, verses: [{ ...chapter.verses[0]!, osis_ref: 'Acts.6.1' }] }
  it('accepts the next chapter and an explicitly empty source chapter', () => {
    expect(validateContinuation(chapter, next, 6)).toBe(next)
    expect(validateContinuation(chapter, { ...next, verses: [] }, 6).verses).toEqual([])
  })
  it('continues from an explicitly empty chapter without substituting a different edition', () => {
    expect(validateContinuation({ ...chapter, verses: [] }, next, 6)).toBe(next)
  })
  it('preserves published canonical references when module chapter numbering differs',()=>{
    const value={...next,verses:[{...next.verses[0]!,osis_ref:'Acts.5.1'}]}
    expect(validateContinuation(chapter,value,6)).toBe(value)
  })
  it.each([
    { ...next, translation: { ...next.translation, code: 'OTHER' } },
    { ...next, chapter: { ...next.chapter, number: 7 } },
    { ...next, book: { ...next.book, slug: 'john' } },
    { ...next, verses: [{ ...next.verses[0]!, osis_ref: 'John.6.1' }] },
    { ...next, verses: [next.verses[0]!, next.verses[0]!] },
  ])('does not silently change edition, book, chapter or verse identity', value => {
    expect(() => validateContinuation(chapter, value, 6)).toThrow()
  })
})
