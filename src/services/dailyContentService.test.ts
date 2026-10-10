import { describe, expect, it, vi } from 'vitest'
import type { BibleApi } from '@/api/client'
import {ApiError} from '@/api/client'
import type { CalendarDay, PrayerDetail } from '@/api/contracts'
import type { DailyContentRepository, StoredCalendarDay, StoredPrayer } from '@/offline/dailyContentRepository'
import { createDailyContentService } from './dailyContentService'
import { apiBaseUrl } from '@/config/api'

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
  it('retains the verified civil edition offline without changing legacy language or text', async () => {
    const storage = repository()
    const onlineApi = api()
    onlineApi.getLiturgicalWorks = vi.fn(async () => [{ id: 1, slug: 'prayer-1', title: prayer.title, collections: ['prayers'], available_languages: ['cu-civil'], source_url: `${apiBaseUrl}/prayers/1`, editions: [{ code: 'civil', title: 'Civil', language: 'cu-civil', orthography: 'civil', reader_profile: 'full' }] }])
    const online = await createDailyContentService(onlineApi, storage).openPrayer(1)
    expect(online.data.text_edition).toEqual({ language: 'cu-civil', orthography: 'civil' })
    expect(online.data.language_code).toBe('ru')
    expect(online.data.body).toBe(prayer.body)
    const offlineApi = api()
    vi.mocked(offlineApi.getPrayer).mockRejectedValue(new ApiError('offline','offline'))
    expect((await createDailyContentService(offlineApi, storage).openPrayer(1)).data.text_edition).toEqual(online.data.text_edition)
  })
  it('keeps declared language and readable prayer when catalogue lookup fails', async () => {
    const source = api()
    source.getLiturgicalWorks = vi.fn(async () => { throw new Error('unavailable') })
    expect((await createDailyContentService(source, repository()).openPrayer(1)).data).toEqual(prayer)
  })
  it('enriches multiple day icons with their exact calendar record associations and caches them', async () => {
    const source = api()
    source.getCalendarDay = vi.fn(async () => ({ ...calendarDay, icons: [
      { id: 1, title: 'First', image_url: '/1' }, { id: 2, title: 'Second', image_url: '/2' },
    ] }))
    source.getCalendarIcon = vi.fn(async (id) => ({ id, calendarRecordIds: [`memory-${id}`] }))
    const service = createDailyContentService(source, repository())
    expect((await service.openCalendarDay('2026-09-18')).data.icons?.[1]?.calendar_record_ids).toEqual(['memory-2'])
    await service.openCalendarDay('2026-09-18')
    expect(source.getCalendarIcon).toHaveBeenCalledTimes(2)
    source.getCalendarIcon = vi.fn(async () => { throw new Error('offline') })
    expect((await createDailyContentService(source, repository()).openCalendarDay('2026-09-18')).data.icons).toHaveLength(2)
  })
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
    vi.mocked(offlineApi.getPrayer).mockRejectedValue(new ApiError('offline','offline'))

    const result = await createDailyContentService(offlineApi, storage).openPrayer(1)

    expect(result.offline).toBe(true)
    expect(result.data).toEqual(prayer)
  })
})
