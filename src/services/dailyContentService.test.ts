import { describe, expect, it, vi } from 'vitest'
import type { BibleApi } from '@/api/client'
import type { CalendarDay, PrayerDetail } from '@/api/contracts'
import type { DailyContentRepository, StoredCalendarDay, StoredPrayer } from '@/offline/dailyContentRepository'
import { createDailyContentService } from './dailyContentService'

const prayer: PrayerDetail = {
  id: 1, language_code: 'ru', category: 'common', liturgy_key: null, title: 'Отче наш',
  short_title: 'Отче наш', intro: null, body: 'Текст', source_url: null, sections: [],
}
const calendarDay: CalendarDay = {
  date: '2026-09-18', old_style_date: '2026-09-05', pascha_date: '2026-04-12',
  liturgical_period: 'Пятница', source: 'bible-desktop-calendar-engine',
  metadata: { apiVersion: '2.1.0', language: 'ru', fastingProfileId: 'typikon-strict' },
  events: [], fasting_events: [], readings: [],
}

function repository(): DailyContentRepository {
  let storedPrayer: StoredPrayer | undefined
  let storedDay: StoredCalendarDay | undefined
  return {
    getPrayer: vi.fn(async () => storedPrayer),
    putPrayer: vi.fn(async (value) => { storedPrayer = value }),
    listPrayers: vi.fn(async () => storedPrayer ? [storedPrayer] : []),
    getCalendarDay: vi.fn(async () => storedDay),
    putCalendarDay: vi.fn(async (value) => { storedDay = value }),
    listCalendarDays: vi.fn(async () => storedDay ? [storedDay] : []),
  }
}

function api(): BibleApi {
  return {
    getTranslations: vi.fn(), getBooks: vi.fn(), getChapter: vi.fn(), getPrayers: vi.fn(),
    getPrayer: vi.fn(async () => prayer),
    getCalendarDay: vi.fn(async () => calendarDay),
  }
}

describe('daily content service', () => {
  it('stores prayers and calendar days after a network read', async () => {
    const storage = repository()
    const service = createDailyContentService(api(), storage)

    expect((await service.openPrayer(1)).offline).toBe(false)
    expect((await service.openCalendarDay('2026-09-18')).offline).toBe(false)
    expect(storage.putPrayer).toHaveBeenCalledOnce()
    expect(storage.putCalendarDay).toHaveBeenCalledOnce()
  })

  it('falls back to a saved prayer when the network is unavailable', async () => {
    const storage = repository()
    await storage.putPrayer({ key: '1', savedAt: '2026-09-18', data: prayer })
    const offlineApi = api()
    vi.mocked(offlineApi.getPrayer).mockRejectedValue(new Error('offline'))

    const result = await createDailyContentService(offlineApi, storage).openPrayer(1)

    expect(result.offline).toBe(true)
    expect(result.data).toEqual(prayer)
  })
})
