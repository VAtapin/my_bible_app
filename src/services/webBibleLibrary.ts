import type { BibleApi } from '@/api/client'
import { ApiError } from '@/api/client'
import type { BibleBook, BibleChapter, TranslationSummary } from '@/api/contracts'
import { readCalendarState, writeCalendarState } from '@/offline/calendarMedia'
import { createLocalProfileRepository } from '@/profile/profileRepository'
import { loadBibleCatalog, localBibles } from './bibleCatalog'
import { createIndexedDbLibraryRepository } from '@/offline/indexedDbLibraryRepository'
import { createIndexedDbChapterRepository } from '@/offline/indexedDbChapterRepository'
import type { ChapterService } from './chapterService'

export const synodalCode = 'BQ_RUSSIAN_RST_STRONG'
const key = 'bible-enabled:web:v1'

/** Web selections are metadata only: enabling never downloads a whole Bible. */
export async function enabledWebBibles(api: BibleApi): Promise<TranslationSummary[]> {
  const saved = await readCalendarState<TranslationSummary[]>(key)
  if (saved?.length) return saved
  const catalog = await loadBibleCatalog(api)
  const profile = typeof window === 'undefined' ? undefined : createLocalProfileRepository(window.localStorage).load()
  const legacy = await localBibles(createIndexedDbLibraryRepository(), createIndexedDbChapterRepository())
  const codes = new Set([synodalCode, ...(profile?.bible.translationCodes ?? []), ...legacy.map(item => item.translation.code)])
  const enabled = catalog.filter(item => codes.has(item.code)).sort((a, b) => Number(b.code === synodalCode) - Number(a.code === synodalCode))
  if (enabled.length) await saveEnabledWebBibles(enabled)
  return enabled
}

export async function saveEnabledWebBibles(editions: TranslationSummary[]): Promise<void> {
  if (!editions.length) throw new Error('At least one Bible must remain enabled')
  await writeCalendarState(key, [...new Map(editions.map(item => [item.code, item])).values()])
}

export async function webBibleBooks(api: BibleApi, code: string): Promise<BibleBook[]> {
  try {
    const books = await api.getBooks(code)
    await writeCalendarState(`bible-books:web:${code}`, books).catch(() => {})
    return books
  } catch (error) {
    if (!isConnectionFailure(error)) throw error
    const saved = await readCalendarState<BibleBook[]>(`bible-books:web:${code}`)
    if (saved) return saved
    const legacy = (await localBibles(createIndexedDbLibraryRepository(), createIndexedDbChapterRepository())).find(item => item.translation.code === code)
    if (legacy) return legacy.books
    throw error
  }
}

export function isConnectionFailure(error: unknown): boolean {
  return error instanceof ApiError && ['offline', 'timeout'].includes(error.kind)
}

/** Online first; old/local chapters remain usable on network failure, never on HTTP rejection. */
export async function readWebChapter(service: ChapterService, code: string, slug: string, number: number): Promise<BibleChapter> {
  try { return await (service.readOnline ? service.readOnline(code, slug, number) : service.download(code, slug, number)) }
  catch (error) {
    if (!isConnectionFailure(error)) throw error
    const stored = await service.readOffline(code, slug, number)
    if (stored) return stored
    throw error
  }
}
