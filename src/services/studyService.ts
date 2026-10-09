import { ApiError } from '@/api/client'
import type { StudyApi, CommentaryEntry } from '@/api/study'
import { readCalendarState, writeCalendarState } from '@/offline/calendarMedia'

export interface StudyCache { read<T>(key: string): Promise<T | undefined>; write(key: string, value: unknown): Promise<void> }
export const studyCache: StudyCache = { read: readCalendarState, write: writeCalendarState }
/** Only network/timeouts allow saved public content; a quota error never hides online text. */
export function createStudyService(api: StudyApi, cache: StudyCache = studyCache) {
  async function cached<T>(key: string, fetch: () => Promise<T>): Promise<T> {
    try { const value = await fetch(); await cache.write(key, value).catch(() => undefined); return value }
    catch (error) {
      if (!(error instanceof ApiError) || !['offline', 'timeout'].includes(error.kind)) throw error
      const value = await cache.read<T>(key)
      if (value === undefined) throw error
      return value
    }
  }
  return {
    books: (query = '', offset = 0) => cached(`study:books:${query}:${offset}`, () => api.books(query, offset)),
    contents: (book: number, offset = 0) => cached(`study:contents:${book}:${offset}`, () => api.contents(book, offset)),
    article: (book: number, section: number) => cached(`study:article:${book}:${section}`, () => api.article(book, section)),
    modules: () => cached('study:modules', () => api.modules()),
    canonicalSlug: (canon: string, osis: string) => cached(`study:canonical:${canon}:${osis}`, () => api.canonicalSlug(canon, osis)),
    commentaries: (book: string, chapter: number | null, modules: string[], offset = 0) => cached(`study:commentaries:${book}:${chapter}:${[...modules].sort().join(',')}:${offset}`, () => api.commentaries(book, chapter, modules, offset)),
  }
}

/** Whole-chapter entries can be filtered without losing range coverage or duplicating introductions. */
export function overlaps(entry: Pick<CommentaryEntry, 'chapter_from' | 'chapter_to' | 'verse_from' | 'verse_to'>, chapter: number, first: number, last: number): boolean {
  if (entry.chapter_from === 0) return true
  const endChapter = entry.chapter_to ?? entry.chapter_from
  if (entry.chapter_from > chapter || endChapter < chapter) return false
  if (entry.verse_from === 0) return true
  const endVerse = entry.verse_to ?? (entry.chapter_to === null ? entry.verse_from : Number.MAX_SAFE_INTEGER)
  return (entry.chapter_from < chapter || entry.verse_from <= last) && (endChapter > chapter || endVerse >= first)
}
