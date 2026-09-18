import type { BibleApi } from '@/api/client'
import type { CalendarDay, PrayerDetail } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { addCalendarDays } from './calendarDates'

export function createDailyContentService(api: BibleApi, repository: DailyContentRepository) {
  return {
    async openPrayer(id: number): Promise<{ data: PrayerDetail; offline: boolean }> {
      try {
        const data = await api.getPrayer(id)
        await repository.putPrayer({ key: String(id), savedAt: new Date().toISOString(), data })
        return { data, offline: false }
      } catch (networkError) {
        const stored = await repository.getPrayer(id)
        if (!stored) throw networkError
        return { data: stored.data, offline: true }
      }
    },
    async openCalendarDay(date: string): Promise<{ data: CalendarDay; offline: boolean }> {
      try {
        const data = await api.getCalendarDay(date)
        await repository.putCalendarDay({ key: date, savedAt: new Date().toISOString(), data })
        return { data, offline: false }
      } catch (networkError) {
        const stored = await repository.getCalendarDay(date)
        if (!stored) throw networkError
        return { data: stored.data, offline: true }
      }
    },
    async downloadCalendarHorizon(
      startDate: string,
      days: number,
      onProgress: (current: number, total: number) => void,
      signal?: AbortSignal,
    ): Promise<void> {
      for (let index = 0; index < days; index += 1) {
        if (signal?.aborted) throw new DOMException('Загрузка остановлена.', 'AbortError')
        const date = addCalendarDays(startDate, index)
        const data = await api.getCalendarDay(date)
        await repository.putCalendarDay({ key: date, savedAt: new Date().toISOString(), data })
        onProgress(index + 1, days)
      }
    },
  }
}
