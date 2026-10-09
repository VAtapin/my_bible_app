import type { BibleApi } from '@/api/client'
import type { VerseSearchResult } from '@/api/contracts'
import { isConnectionFailure } from './webBibleLibrary'
import type { ChapterRepository } from '@/offline/chapterRepository'
import {prepareSearchStemming,searchStem,supportsSearchStemming} from './searchStemming'

export type SearchMatch = 'exact' | 'phrase' | 'partial' | 'strong' | 'morphology'
export type SearchScope = 'all' | 'old' | 'new' | 'psalms'
export interface SearchOptions { match: SearchMatch; scope: SearchScope; offset: number; book?: string }
export function searchKey(value: string) { return value.normalize('NFC').toLocaleLowerCase().trim() }
export function readableSearchText(value: string) { return value.replace(/<[^>]*>/gu, '').replace(/\b[HG]\d{1,5}\b/gu, '').replace(/\s+/gu, ' ').trim() }
function words(value: string): string[] { return searchKey(value).match(/[\p{L}\p{M}\p{N}]+/gu) ?? [] }
export function matchesVerse(text: string, query: string, match: SearchMatch, raw = text,language=''): boolean {
  if (match === 'morphology') {
    if(!supportsSearchStemming(language))throw new Error('Word forms require a supported language')
    const stems=words(text).map(word=>searchStem(word,language))
    return words(query).length>0&&words(query).every(word=>stems.includes(searchStem(word,language)))
  }
  if (match === 'strong') {
    const markers: string[] = raw.match(/\b[HG]\d{1,5}\b/gu) ?? []
    return /^[HG]\d{1,5}$/u.test(query.toUpperCase().trim()) && markers.includes(query.toUpperCase().trim())
  }
  const tokens = words(query), source = words(text)
  if (!tokens.length) return false
  if (match === 'partial') return tokens.every(token => searchKey(text).includes(token))
  if (match === 'exact') return tokens.every(token => source.includes(token))
  return source.some((_, index) => tokens.every((token, n) => source[index + n] === token))
}
export function searchSegments(text: string, query: string, match: SearchMatch,language='') {
  if (match === 'strong') return [{ text, match: false }]
  const tokens = new Set(words(query))
  return text.split(/([\p{L}\p{M}\p{N}]+)/gu).filter(Boolean).map(text => ({ text, match: [...tokens].some(token => match === 'morphology'&&supportsSearchStemming(language) ? searchStem(text,language)===searchStem(token,language) : match === 'partial' ? searchKey(text).includes(token) : searchKey(text) === token) }))
}
export interface SearchPage { results: VerseSearchResult[]; next: number; more: boolean; local: boolean }
/** The server may return fuzzy fallback: validate exact/phrase/partial against full verse text. */
export async function searchVersePage(api: BibleApi, chapters: ChapterRepository, code: string, query: string, options: SearchOptions): Promise<SearchPage> {
  if(options.match==='morphology')await prepareSearchStemming()
  try {
    const response = await api.searchVerses(query, code, { match: options.match === 'morphology' ? 'all_words' : 'partial', scope: options.scope, offset: options.offset, limit: 50 })
    const scoped=response.results.filter(item=>!options.book||item.book.osis_code===options.book)
    return { results: options.match === 'morphology' ? scoped : scoped.filter(item => matchesVerse(readableSearchText(item.text ?? item.snippet), query, options.match, item.text)),
      next: options.offset + response.results.length, more: response.results.length === 50, local: false }
  } catch (error) {
    if (!isConnectionFailure(error)) throw error
    if (options.scope === 'old' || options.scope === 'new') throw new Error('Testament scope needs online metadata')
    const results: VerseSearchResult[] = []
    for (const { data } of await chapters.list()) {
      if (data.translation.code !== code) continue
      const osis = data.verses[0]?.osis_ref.split('.')[0]
      if (options.book && osis !== options.book) continue
      // Scope metadata is needed: never infer testament from an arbitrary edition's order.
      if (options.scope !== 'all' && !(options.scope === 'psalms' && osis === 'Ps')) continue
      for (const verse of data.verses) {
        if (!matchesVerse(verse.plain_text, query, options.match, verse.has_strong_markup ? verse.text : '',data.translation.language.code)) continue
        results.push({ verse_id: verse.id, reference: `${data.book.name} ${data.chapter.number}:${verse.number}`, translation: data.translation,
          book: data.book, chapter_number: data.chapter.number, verse_number: verse.number, snippet: verse.plain_text, text: verse.text })
      }
    }
    const page = results.slice(options.offset, options.offset + 50)
    return { results: page, next: options.offset + page.length, more: results.length > options.offset + 50, local: true }
  }
}
