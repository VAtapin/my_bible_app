import {describe, expect, it} from 'vitest'
import {createStudyApi} from './study'

/** Explicitly opt in to read-only acceptance of a deployed study API. */
describe.skipIf(!process.env.STUDY_API)('published general commentary API', () => {
  const api = createStudyApi({baseUrl: process.env.STUDY_API!})
  for (const book of ['1john', '1corinthians']) {
    it(`opens actual general materials for ${book} through the client contract`, async () => {
      const page = await api.commentaries(book, 1, ['OPTINA_COMMENTARIES'], 0, 'general')
      expect(page.book).toBe(book)
      expect(page.chapter).toBe(1)
      expect(page.total).toBeGreaterThan(0)
      expect(page.total).toBeGreaterThanOrEqual(page.entries.length)
      expect(page.entries.length).toBeGreaterThan(0)
      for (const entry of page.entries) {
        expect(entry.module_code).toBe('OPTINA_COMMENTARIES')
        expect(entry.chapter_from === 0 || entry.verse_from === 0).toBe(true)
        expect(entry.body.replace(/<[^>]*>/gu, '').trim()).not.toBe('')
        expect([undefined, null, 'before', 'after']).toContain(entry.placement)
      }
    })
  }
})
