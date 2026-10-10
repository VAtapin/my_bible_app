import { createApiRequest, type ApiClientOptions } from './client'
export interface DictionaryModule { code: string; name: string; language_code: string | null; kind: string; content_version: string | null; entries_count: number; media_count: number; word_forms_count: number }
export interface DictionaryTopic { id: number; key: string; topic: string; module_code?: string; module_name?: string }
export interface DictionaryArticle extends DictionaryTopic { body: string; media: { id: number; fragment_id: string; url: string }[]; links: { label: string; key: string; topic: string }[]; references: { book_slug: string; book_osis?: string | null; chapter_number: number | null; verse_from: number | null; verse_to: number | null }[] }
export interface DictionaryPage { data: DictionaryTopic[]; total: number }
const record = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null
const count = (v: unknown): v is number => Number.isSafeInteger(v) && Number(v) >= 0
const nullable = (v: unknown) => v === null || typeof v === 'string'
export const isDictionaryTopic = (v: unknown): v is DictionaryTopic => record(v) && count(v.id) && v.id > 0 && typeof v.topic === 'string' && typeof v.key === 'string' && /^[a-f0-9]{40}$/.test(v.key)
export const isDictionaryArticle = (v: unknown): v is DictionaryArticle => isDictionaryTopic(v) && record(v) && typeof v.body === 'string'
  && Array.isArray(v.media) && v.media.every(m => record(m) && count(m.id) && typeof m.fragment_id === 'string' && typeof m.url === 'string')
  && Array.isArray(v.links) && v.links.every(l => record(l) && typeof l.label === 'string' && typeof l.topic === 'string' && typeof l.key === 'string' && /^[a-f0-9]{40}$/.test(l.key))
  && Array.isArray(v.references) && v.references.every(r => record(r) && typeof r.book_slug === 'string' && [r.chapter_number, r.verse_from, r.verse_to].every(n => n === null || count(n)))
const page = (v: unknown): v is DictionaryPage => record(v) && count(v.total) && Array.isArray(v.data) && v.data.every(isDictionaryTopic)
export function dictionaryMediaUrl(base: string, code: string, id: number, url: string): string {
  const expected = `/api/dictionaries/${encodeURIComponent(code)}/media/${id}`
  const resolved = new URL(url, base), origin = new URL(base)
  if (resolved.origin !== origin.origin || resolved.pathname !== expected || resolved.search || resolved.hash) throw new Error('Invalid dictionary image')
  return resolved.href
}
export function createDictionaryApi(options: ApiClientOptions) {
  const request = createApiRequest(options)
  const module = (code: string) => { if (!/^[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(code)) throw new Error('Invalid module'); return encodeURIComponent(code) }
  const params = (q: string, offset: number) => { if (!count(offset)) throw new Error('Invalid offset'); return new URLSearchParams({ q: q.slice(0, 120), offset: String(offset), limit: '30' }) }
  return {
    async modules() { return (await request('/dictionaries', (v): v is { data: DictionaryModule[] } => record(v) && Array.isArray(v.data) && v.data.every(m => record(m) && typeof m.code === 'string' && typeof m.name === 'string' && nullable(m.language_code) && nullable(m.content_version) && typeof m.kind === 'string' && [m.entries_count, m.media_count, m.word_forms_count].every(count)))).data },
    entries: (code: string, q = '', offset = 0) => request(`/dictionaries/${module(code)}/entries?${params(q, offset)}`, page),
    async article(code: string, key: string) {
      if (!/^[a-f0-9]{40}$/.test(key)) throw new Error('Invalid topic')
      return (await request(`/dictionaries/${module(code)}/entries/${key}`, (v): v is { data: DictionaryArticle } => record(v) && isDictionaryArticle(v.data) && v.data.key === key && v.data.media.every(m => { try { dictionaryMediaUrl(options.baseUrl, code, m.id, m.url); return true } catch { return false } }))).data
    },
    async lookup(q: string, codes: string[] = []) {
      if (!q.trim() || codes.length > 30) throw new Error('Invalid lookup')
      const query = params(q, 0); if (codes.length) query.set('modules', codes.join(','))
      return (await request(`/dictionaries/lookup?${query}`, (v): v is { data: { module_code: string; standard_form: string }[] } => record(v) && Array.isArray(v.data) && v.data.every(f => record(f) && typeof f.module_code === 'string' && typeof f.standard_form === 'string'))).data
    },
    context(book: string, chapter: number | null, codes: string[], offset = 0) {
      if (!/^[A-Za-z0-9_-]+$/.test(book) || codes.length > 30 || (chapter !== null && (!count(chapter) || chapter === 0))) throw new Error('Invalid context')
      const query = params('', offset); query.set('modules', codes.join(','))
      return request(`/bible/books/${book}${chapter === null ? '' : `/chapters/${chapter}`}/dictionary-entries?${query}`, page)
    },
    verse(verse: number, codes: string[], offset = 0) { if (!count(verse) || !verse || codes.length > 30) throw new Error('Invalid context'); const query = params('', offset); query.set('modules', codes.join(',')); return request(`/verses/${verse}/dictionary-entries?${query}`, page) },
  }
}
export type DictionaryApi = ReturnType<typeof createDictionaryApi>
