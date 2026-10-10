import { createApiRequest, type ApiClientOptions } from './client'
import { isCommentaryAnnotations,type CommentaryAnnotations } from './commentaryAnnotations'

export interface StudyBook { id: number; slug: string; title: string; author: string | null; description: string | null; module_code: string }
export interface StudySection {
  id: number; title: string | null; author: string | null; chapter_from: number; verse_from: number
  chapter_to: number | null; verse_to: number | null; book_osis_code: string | null; annotations?:CommentaryAnnotations|null
}
export interface StudyArticle extends StudySection { body: string }
export interface CommentaryEntry extends Omit<StudyArticle, 'book_osis_code'> {
  commentary_book_id: number | null; module_code: string; module_name: string
}
export interface CommentaryModule { code: string; name: string; short_name: string | null; entries_count: number }
export interface BookContents { book: StudyBook; sections: StudySection[]; total: number }
export interface CommentaryPage { book: string; chapter: number | null; entries: CommentaryEntry[]; total: number }
export interface BookPage { data: StudyBook[]; total: number }
const record = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null
const number = (v: unknown): v is number => Number.isSafeInteger(v) && Number(v) >= 0
const nullableText = (v: unknown) => v === null || typeof v === 'string'
export const isStudyBook = (v: unknown): v is StudyBook => record(v) && number(v.id) && v.id > 0 && typeof v.slug === 'string'
  && typeof v.title === 'string' && nullableText(v.author) && nullableText(v.description) && typeof v.module_code === 'string'
export const isStudySection = (v: unknown): v is StudySection => record(v) && number(v.id) && v.id > 0
  && nullableText(v.title) && nullableText(v.author) && number(v.chapter_from) && number(v.verse_from)
  && (v.chapter_to === null || number(v.chapter_to)) && (v.verse_to === null || number(v.verse_to)) && nullableText(v.book_osis_code) && (v.annotations===undefined||v.annotations===null||isCommentaryAnnotations(v.annotations))
const range = (v: Record<string, unknown>) => number(v.id) && v.id > 0 && nullableText(v.title) && nullableText(v.author)
  && number(v.chapter_from) && number(v.verse_from) && (v.chapter_to === null || number(v.chapter_to)) && (v.verse_to === null || number(v.verse_to))
export const isCommentaryEntry = (v: unknown): v is CommentaryEntry => record(v) && range(v) && typeof v.body === 'string'
  && (v.commentary_book_id === null || number(v.commentary_book_id)) && typeof v.module_code === 'string' && typeof v.module_name === 'string' && (v.annotations===undefined||v.annotations===null||isCommentaryAnnotations(v.annotations))
export const isBookContents = (v: unknown): v is BookContents => record(v) && isStudyBook(v.book) && number(v.total)
  && Array.isArray(v.sections) && v.sections.every(isStudySection)
const envelope = <T>(validate: (v: unknown) => v is T) => (v: unknown): v is { data: T } => record(v) && validate(v.data)
const pageQuery = (offset: number, limit = 20) => { if (!number(offset)) throw new Error('Invalid offset'); return new URLSearchParams({ offset: String(offset), limit: String(limit) }) }
const id = (value: number) => { if (!number(value) || !value) throw new Error('Invalid identifier'); return value }

export function createStudyApi(options: ApiClientOptions) {
  const request = createApiRequest(options)
  return {
    books(q = '', offset = 0) {
      const params = pageQuery(offset); params.set('q', q.slice(0, 100))
      return request<BookPage>(`/books?${params}`, (v): v is BookPage => record(v) && number(v.total) && Array.isArray(v.data) && v.data.every(isStudyBook))
    },
    async contents(book: number, offset = 0) {
      return (await request(`/books/${id(book)}?${pageQuery(offset)}`, envelope((v): v is BookContents => isBookContents(v) && v.book.id === book))).data
    },
    async article(book: number, section: number) {
      return (await request(`/books/${id(book)}/sections/${id(section)}`, envelope((v): v is StudyArticle => isStudySection(v)
        && v.id === section && record(v) && typeof v.body === 'string'))).data
    },
    async modules() {
      return (await request('/commentary-modules', envelope((v): v is CommentaryModule[] => Array.isArray(v) && v.every(m => record(m)
        && typeof m.code === 'string' && typeof m.name === 'string' && nullableText(m.short_name) && number(m.entries_count))))).data
    },
    async canonicalSlug(canon: string, osis: string) {
      const response = await request(`/canons/${encodeURIComponent(canon)}/books`, envelope((v): v is { books: { slug: string; osis_code: string }[] } => record(v)
        && Array.isArray(v.books) && v.books.every(b => record(b) && typeof b.slug === 'string' && typeof b.osis_code === 'string')))
      const slug = response.data.books.find(b => b.osis_code === osis)?.slug
      if (!slug) throw new Error('Canonical book unavailable')
      return slug
    },
    async canonicalBooks(canon: string) {
      return (await request(`/canons/${encodeURIComponent(canon)}/books`, envelope((v): v is { books: { slug: string; osis_code: string }[] } => record(v)
        && Array.isArray(v.books) && v.books.every(b => record(b) && typeof b.slug === 'string' && typeof b.osis_code === 'string')))).data.books
    },
    async commentaries(book: string, chapter: number | null, modules: string[], offset = 0) {
      if (!modules.length || modules.length > 30) throw new Error('Invalid source selection')
      const params = pageQuery(offset, 10); params.set('modules', [...new Set(modules)].sort().join(','))
      const route = chapter === null ? '' : `/chapters/${id(chapter)}`
      return (await request(`/bible/books/${encodeURIComponent(book)}${route}/commentaries?${params}`, envelope((v): v is CommentaryPage => record(v)
        && v.book === book && v.chapter === chapter && number(v.total) && Array.isArray(v.entries) && v.entries.every(isCommentaryEntry)))).data
    },
  }
}
export type StudyApi = ReturnType<typeof createStudyApi>
