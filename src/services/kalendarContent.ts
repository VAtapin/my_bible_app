import type { BibleApi } from '@/api/client'
import { ApiError } from '@/api/client'
import type { CalendarDay } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { createDailyContentService } from './dailyContentService'
import { addCalendarDays } from './calendarDates'
import { createCalendarMediaCache, readCalendarState, writeCalendarState, type CalendarMediaCache } from '@/offline/calendarMedia'
import type { CalendarGridDay } from '@/api/kalendar'

// Kalendar currently publishes RU/DE/UK/CU, not EN. Do not invent an English corpus.
export const calendarContentLanguage = (language: string): string => language === 'en' ? 'ru' : language
export interface CalendarHorizon { from: string; to: string; daysSaved: number; missingAssets: number; missingServices: number }
export const horizonKey = (language: string) => `calendar-horizon:${calendarContentLanguage(language)}`
export const calendarServiceKey = (date: string, language: string) => `calendar-service:${language}:${date}`

async function abortable<T>(promise: Promise<T>, signal?: AbortSignal): Promise<T> {
  if (!signal) return promise
  if (signal.aborted) throw new DOMException('Aborted', 'AbortError')
  return new Promise<T>((resolve, reject) => {
    const stop = () => reject(new DOMException('Aborted', 'AbortError'))
    signal.addEventListener('abort', stop, { once: true })
    promise.then(resolve, reject).finally(() => signal.removeEventListener('abort', stop))
  })
}

/** Kalendar supplies the complete day. The existing Bible API keeps its offline workflow. */
export function createCalendarContentService(api: Pick<BibleApi, 'getCalendarDay'> & { getMonth?: (date: string, language: string) => Promise<CalendarGridDay[]> }, bibleApi: BibleApi, repository: DailyContentRepository, media: CalendarMediaCache = createCalendarMediaCache()) {
  const existing = createDailyContentService(bibleApi, repository)
  return {
    async openCalendarDay(date: string, language = 'ru'): Promise<{ data: CalendarDay; offline: boolean }> {
      language = calendarContentLanguage(language)
      try { return { data: await api.getCalendarDay(date, language), offline: false } }
      catch (error) {
        // Do not hide revoked access, invalid data or rate limits with stale API data.
        if (!(error instanceof ApiError) || !['offline', 'timeout'].includes(error.kind)) throw error
        const saved = await repository.getCalendarDay(`${language}:${date}`)
        if (saved?.data.source === 'kalendar-api') return { data: saved.data, offline: true }
        return existing.openCalendarDay(date, language)
      }
    },
    async downloadCalendarHorizon(startDate: string, days: number, onProgress: (current: number, total: number) => void, signal?: AbortSignal, language = 'ru'): Promise<CalendarHorizon> {
      language = calendarContentLanguage(language)
      const result: CalendarHorizon = { from: startDate, to: startDate, daysSaved: 0, missingAssets: 0, missingServices: 0 }
      const attempted = new Map<string, Promise<boolean>>()
      const savedMonths = new Set<string>()
      for (let index = 0; index < days; index++) {
        if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
        const date = addCalendarDays(startDate, index)
        if (api.getMonth && !savedMonths.has(date.slice(0, 7))) {
          const month = await abortable(api.getMonth(date, language), signal)
          await writeCalendarState(`calendar-month:${language}:${date.slice(0, 7)}`, month)
          savedMonths.add(date.slice(0, 7))
        }
        const data = await abortable(api.getCalendarDay(date, language), signal)
        const assets = new Map<string, boolean>()
        // One bounded preview per icon, never its full-resolution gallery.
        for (const icon of data.icons ?? []) if (icon.image_url) assets.set(icon.image_url, true)
        for (const event of data.events) if (event.typikon_mark) assets.set(event.typikon_mark.image_url, false)
        if (data.food?.image_url) assets.set(data.food.image_url, false)
        for (const marker of data.memorial_markers ?? []) assets.set(marker.image_url, false)
        const entries = [...assets]
        for (let start = 0; start < entries.length; start += 4) {
          const outcomes = await Promise.all(entries.slice(start, start + 4).map(([url, preview]) => {
            if (!attempted.has(url)) attempted.set(url, media.save(url, preview, signal).then(() => true, (error: unknown) => {
              if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
              if (error instanceof DOMException && error.name === 'QuotaExceededError') throw error
              return false
            }))
            return attempted.get(url)!
          }))
          result.missingAssets += outcomes.filter((saved) => !saved).length
        }
        try {
          const serviceLanguage = language === 'cu' ? 'cu' : 'cu-civil'
          const plan = await abortable(bibleApi.getCalendarService(date, serviceLanguage), signal)
          await writeCalendarState(calendarServiceKey(date, serviceLanguage), plan)
        } catch (error) {
          if (signal?.aborted || (error instanceof DOMException && error.name === 'QuotaExceededError')) throw error
          result.missingServices++
        }
        if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
        await repository.putCalendarDay({ key: `${language}:${date}`, savedAt: new Date().toISOString(), data })
        result.to = date; result.daysSaved = index + 1
        await writeCalendarState(horizonKey(language), { ...result })
        onProgress(index + 1, days)
      }
      return result
    },
    getSavedHorizon: (language: string) => readCalendarState<CalendarHorizon>(horizonKey(language)),
  }
}
