import type { BibleChapter, BibleVerse } from '@/api/contracts'
import type { BibleApi } from '@/api/client'
import type { ChapterService } from './chapterService'
import { readWebChapter, webBibleBooks } from './webBibleLibrary'

export function compareVerses(primary: BibleChapter, secondary: BibleChapter) {
  const index = (verses: BibleVerse[]) => {
    const result = new Map(verses.map(verse => [verse.osis_ref, verse]))
    if (result.size !== verses.length || verses.some(verse => !verse.osis_ref)) throw new Error('Invalid verse references')
    return result
  }
  const first = index(primary.verses), second = index(secondary.verses)
  return [...new Set([...first.keys(), ...second.keys()])]
    .sort((a, b) => Number(a.split('.').at(-1)) - Number(b.split('.').at(-1)) || a.localeCompare(b))
    .map(reference => ({ reference, primary: first.get(reference), secondary: second.get(reference) }))
}

export async function loadComparison(primary: BibleChapter, code: string, api: BibleApi, service: ChapterService): Promise<BibleChapter> {
  if (!code || code === primary.translation.code) throw new Error('Invalid translation')
  // OSIS comes from actual verse references; titles and slugs are not book identities.
  const canonical = primary.verses[0]?.osis_ref.split('.')[0]
  if (!canonical) throw new Error('Unavailable canonical book')
  const stored = (await service.listStored()).find(({ data }) => data.translation.code === code
    && data.chapter.number === primary.chapter.number && data.verses[0]?.osis_ref.split('.')[0] === canonical)?.data
  const validate = (value: BibleChapter) => {
    if (value.translation.code !== code || value.chapter.number !== primary.chapter.number
      || value.verses.some(verse => verse.osis_ref.split('.')[0] !== canonical)) throw new Error('Invalid comparison chapter')
    compareVerses(primary, value)
    return value
  }
  if (stored) return validate(stored)
  const books = await webBibleBooks(api, code)
  const book = books.find(item => item.canonical_book?.osis_code === canonical)
  if (!book || book.chapters_count < primary.chapter.number) throw new Error('Unavailable canonical book')
  return validate(await readWebChapter(service, code, book.slug, primary.chapter.number))
}
