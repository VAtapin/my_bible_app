import type { BibleApi } from '@/api/client'
import { ApiError } from '@/api/client'
import type { CalendarDay } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { createDailyContentService } from './dailyContentService'

/** Kalendar supplies the complete day. The existing Bible API keeps its offline workflow. */
export function createCalendarContentService(api: Pick<BibleApi, 'getCalendarDay'>, bibleApi: BibleApi, repository: DailyContentRepository) {
  const existing = createDailyContentService(bibleApi, repository)
  return {
    async openCalendarDay(date: string, language = 'ru'): Promise<{ data: CalendarDay; offline: boolean }> {
      try { return { data: await api.getCalendarDay(date, language), offline: false } }
      catch (error) {
        // Do not hide revoked access, invalid data or rate limits with stale API data.
        if (!(error instanceof ApiError) || !['offline', 'timeout'].includes(error.kind)) throw error
        return existing.openCalendarDay(date, language)
      }
    },
    downloadCalendarHorizon: existing.downloadCalendarHorizon,
  }
}
