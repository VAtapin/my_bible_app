import type { BibleBook, TranslationSummary, VerseSearchResult } from '@/api/contracts'
import type { BibleApi } from '@/api/client'
import { ApiError } from '@/api/client'
import families from '../../config/bible-language-groups.json'
import type { LibraryRepository, OfflinePackage } from '@/offline/libraryRepository'
import { isInstalledPackage } from '@/offline/libraryRepository'
import type { ChapterRepository } from '@/offline/chapterRepository'
import { readCalendarState as readState, writeCalendarState as writeState } from '@/offline/calendarMedia'

/** Same exact Glottolog matches as native Android; unmapped languages are never hidden. */
export function translationGroup(edition: TranslationSummary): string {
  return (families as Record<string, string>)[edition.language.code] ?? 'other'
}
export function catalogSearchKey(value: string): string {
  return value.normalize('NFD').replace(/\p{M}+/gu, '').toLocaleLowerCase().replaceAll('ё', 'е').trim()
}
export function matchingTranslations(catalog: TranslationSummary[], query: string, group = '', code = '') {
  const tokens = catalogSearchKey(query).split(/\s+/u).filter(Boolean)
  return catalog.filter(item => (!group || translationGroup(item) === group) && (!code || item.language.code === code)
    && tokens.every(token => catalogSearchKey([item.name, item.short_name, item.code, item.language.code, item.language.name, item.language.native_name].join(' ')).includes(token)))
    .sort((a, b) => (a.language.native_name ?? a.language.name).localeCompare(b.language.native_name ?? b.language.name) || a.name.localeCompare(b.name))
}

export async function loadBibleCatalog(api: BibleApi): Promise<TranslationSummary[]> {
  try {
    const catalog = await api.getTranslations()
    await writeState('bible-catalog:available:v1', catalog)
    return catalog
  } catch (error) {
    if (!(error instanceof ApiError) || !['offline', 'timeout'].includes(error.kind)) throw error
    const saved = await readState<TranslationSummary[]>('bible-catalog:available:v1')
    if (saved) return saved
    throw error
  }
}

export interface InstalledBible { translation: TranslationSummary; books: BibleBook[]; package: OfflinePackage }
export async function localBibles(library: LibraryRepository, chapters: ChapterRepository): Promise<InstalledBible[]> {
  const packages = await library.listPackages()
  // Old whole-translation packages survive; reconstruct only metadata, not text or a new database.
  const legacy = packages.some(pack => !pack.translation || !pack.books) ? await chapters.list() : []
  return packages.flatMap(pack => {
    const saved = legacy.filter(row => row.data.translation.code === pack.translationCode)
    const original = saved[0]?.data.translation
    const translation: TranslationSummary | undefined = pack.translation ?? (original ? {
      ...original, canon_code: null, has_old_testament: true, has_new_testament: true, has_apocrypha: false, has_strong: false, is_default: false,
    } : undefined)
    const books = pack.books ?? [...new Map(saved.map(row => [row.data.book.slug, { ...row.data.book, order: 0 }])).values()]
    return translation ? [{ translation, books, package: pack }] : []
  })
}
export async function installedBibles(library: LibraryRepository, chapters: ChapterRepository) {
  return (await localBibles(library, chapters)).filter(item => isInstalledPackage(item.package))
}

export async function searchLocalBible(chapters: ChapterRepository, code: string, query: string): Promise<VerseSearchResult[]> {
  const tokens = catalogSearchKey(query).split(/\s+/u).filter(Boolean)
  if (!tokens.length) return []
  const results: VerseSearchResult[] = []
  for (const { data } of await chapters.list()) {
    if (data.translation.code !== code) continue
    for (const verse of data.verses) {
      if (!verse.plain_text.trim() || !tokens.every(token => catalogSearchKey(verse.plain_text).includes(token))) continue
      results.push({ verse_id: verse.id, reference: `${data.book.name} ${data.chapter.number}:${verse.number}`,
        translation: data.translation, book: data.book, chapter_number: data.chapter.number,
        verse_number: verse.number, snippet: verse.plain_text.slice(0, 400) })
      if (results.length >= 30) return results
    }
  }
  return results
}
