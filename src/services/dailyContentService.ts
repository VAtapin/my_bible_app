import type { BibleApi } from '@/api/client'
import type { CalendarDay } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { addCalendarDays } from './calendarDates'
import { linkedPrayerEdition, type PresentedPrayer } from './prayerEditions'
import { apiBaseUrl } from '@/config/api'

export function createDailyContentService(api: BibleApi, repository: DailyContentRepository) {
  const iconMappings = new Map<number, string[]>()
  return {
    async openPrayer(id: number): Promise<{ data: PresentedPrayer; offline: boolean }> {
      try {
        const data: PresentedPrayer = { ...await api.getPrayer(id) }
        try {
          if (typeof api.getLiturgicalWorks === 'function') {
            const edition = linkedPrayerEdition(id, await api.getLiturgicalWorks('prayers') ?? [], apiBaseUrl)
            if (edition) data.text_edition = edition
          }
        } catch { /* A catalogue failure must not hide a prayer or guess its language. */ }
        await repository.putPrayer({ key: String(id), savedAt: new Date().toISOString(), data })
        return { data, offline: false }
      } catch (networkError) {
        const stored = await repository.getPrayer(id)
        if (!stored) throw networkError
        return { data: stored.data, offline: true }
      }
    },
    async openCalendarDay(date: string, language = 'ru'): Promise<{ data: CalendarDay; offline: boolean }> {
      try {
        const data = await api.getCalendarDay(date, language)
        if ((data.icons?.length ?? 0) > 1) {
          await Promise.all(data.icons!.map(async (icon) => {
            if (icon.calendar_record_ids) return
            try {
              const ids = iconMappings.get(icon.id) ?? (await api.getCalendarIcon(icon.id)).calendarRecordIds
              iconMappings.set(icon.id, ids)
              icon.calendar_record_ids = ids
            } catch { /* A missing catalogue must not hide the actual day or its icons. */ }
          }))
        }
        await repository.putCalendarDay({ key: `${language}:${date}`, savedAt: new Date().toISOString(), data })
        return { data, offline: false }
      } catch (networkError) {
        const stored = await repository.getCalendarDay(`${language}:${date}`) ?? await repository.getCalendarDay(date)
        if (!stored) throw networkError
        return { data: stored.data, offline: true }
      }
    },
    async downloadCalendarHorizon(
      startDate: string,
      days: number,
      onProgress: (current: number, total: number) => void,
      signal?: AbortSignal,
      language = 'ru',
    ): Promise<void> {
      for (let index = 0; index < days; index += 1) {
        if (signal?.aborted) throw new DOMException('Загрузка остановлена.', 'AbortError')
        const date = addCalendarDays(startDate, index)
        const data = await api.getCalendarDay(date, language)
        await repository.putCalendarDay({ key: `${language}:${date}`, savedAt: new Date().toISOString(), data })
        onProgress(index + 1, days)
      }
    },
  }
}
