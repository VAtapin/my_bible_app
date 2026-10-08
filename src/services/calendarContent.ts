import type { BibleApi } from '@/api/client'
import { ApiError } from '@/api/client'
import type { CalendarDay } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { addCalendarDays } from './calendarDates'
import { createCalendarMediaCache, readCalendarState, writeCalendarState, type CalendarMediaCache } from '@/offline/calendarMedia'
import type { CalendarGridDay } from '@/api/calendar'

// BibleDesktop currently publishes RU/DE/UK/CU calendar text, not EN.
export const calendarContentLanguage = (language: string): string => language === 'en' ? 'ru' : language
export interface CalendarHorizon { from: string; to: string; daysSaved: number; missingAssets: number; missingServices: number }
export const horizonKey = (language: string) => `bible-calendar-horizon:${calendarContentLanguage(language)}`
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

export function waitForCalendarRequest(ms: number, signal?: AbortSignal): Promise<void> {
  if (signal?.aborted) return Promise.reject(new DOMException('Aborted', 'AbortError'))
  if (ms <= 0) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const stop = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal?.removeEventListener('abort', stop); resolve() }, ms)
    signal?.addEventListener('abort', stop, { once: true })
  })
}

/** BibleDesktop is the ONLY data API, including calendar days and months. */
export function createCalendarContentService(api: Pick<BibleApi, 'getCalendarDay' | 'getCalendarService'> & { getCalendarMonth?: (date: string, language: string) => Promise<CalendarGridDay[]> }, repository: DailyContentRepository, media: CalendarMediaCache = createCalendarMediaCache(), wait = waitForCalendarRequest) {
  return {
    async openCalendarDay(date: string, language = 'ru'): Promise<{ data: CalendarDay; offline: boolean }> {
      language = calendarContentLanguage(language)
      try { return { data: await api.getCalendarDay(date, language), offline: false } }
      catch (error) {
        // Do not hide revoked access, invalid data or rate limits with stale API data.
        if (!(error instanceof ApiError) || !['offline', 'timeout'].includes(error.kind)) throw error
        const saved = await repository.getCalendarDay(`${language}:${date}`)
        if (saved?.data.source === 'bible-desktop-calendar-engine') return { data: saved.data, offline: true }
        throw error
      }
    },
    async downloadCalendarHorizon(startDate: string, days: number, onProgress: (current: number, total: number) => void, signal?: AbortSignal, language = 'ru'): Promise<CalendarHorizon> {
      language = calendarContentLanguage(language)
      const result: CalendarHorizon = { from: startDate, to: startDate, daysSaved: 0, missingAssets: 0, missingServices: 0 }
      const attempted = new Map<string, Promise<boolean>>()
      const previews = new Map<string, string>()
      const savedMonths = new Set<string>()
      const saveAssets = async (assets: Map<string, boolean>) => {
        const entries = [...assets]
        for (let start = 0; start < entries.length; start += 4) {
          const outcomes = await Promise.all(entries.slice(start, start + 4).map(([url, preview]) => {
            if (!attempted.has(url)) attempted.set(url, (previews.has(url)
              ? media.save(url, preview, signal, previews.get(url))
              : media.save(url, preview, signal)).then(() => true, (error: unknown) => {
              if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
              if (error instanceof DOMException && error.name === 'QuotaExceededError') throw error
              return false
            }))
            return attempted.get(url)!
          }))
          result.missingAssets += outcomes.filter((saved) => !saved).length
        }
      }
      let lastRequestAt: number | undefined
      // Existing BibleDesktop calendar routes share a 30/minute budget.
      // Pace explicit downloads; never weaken server limits or replace a 429 with old data.
      const downloadRequest = async <T>(run: () => Promise<T>): Promise<T> => {
        for (let attempt = 0; ; attempt++) {
          if (lastRequestAt !== undefined) await wait(Math.max(0, lastRequestAt + 2100 - Date.now()), signal)
          if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
          lastRequestAt = Date.now()
          try { return await abortable(run(), signal) }
          catch (error) {
            if (!(error instanceof ApiError) || error.status !== 429 || attempt > 0) throw error
            await wait(Math.max(2100, error.retryAfterMs ?? 61_000), signal)
          }
        }
      }
      for (let index = 0; index < days; index++) {
        if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
        const date = addCalendarDays(startDate, index)
        if (api.getCalendarMonth && !savedMonths.has(date.slice(0, 7))) {
          const month = await downloadRequest(() => api.getCalendarMonth!(date, language))
          // The whole saved grid must retain its signs, including dates outside the 30-day range.
          const marks = new Map<string, boolean>()
          for (const day of month) for (const event of day.events) if (event.typikonMark) marks.set(event.typikonMark.svgSource, false)
          await saveAssets(marks)
          await writeCalendarState(`bible-calendar-month:${language}:${date.slice(0, 7)}`, month)
          savedMonths.add(date.slice(0, 7))
        }
        const data = await downloadRequest(() => api.getCalendarDay(date, language))
        const assets = new Map<string, boolean>()
        // One bounded preview per icon, never its full-resolution gallery.
        for (const icon of data.icons ?? []) if (icon.image_url) {
          assets.set(icon.image_url, true)
          if (icon.imagePreviewUrl) previews.set(icon.image_url, icon.imagePreviewUrl)
        }
        for (const event of data.events) if (event.typikon_mark) assets.set(event.typikon_mark.image_url, false)
        if (data.food?.image_url) assets.set(data.food.image_url, false)
        for (const marker of data.memorial_markers ?? []) assets.set(marker.image_url, false)
        await saveAssets(assets)
        try {
          const serviceLanguage = language === 'cu' ? 'cu' : 'cu-civil'
          const plan = await downloadRequest(() => api.getCalendarService(date, serviceLanguage))
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
