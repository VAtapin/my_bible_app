import { beforeEach, describe, expect, it, vi } from 'vitest'
import { IDBFactory } from 'fake-indexeddb'
import { ApiError, type BibleApi } from '@/api/client'
import type { CalendarDay } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { createCalendarContentService } from './kalendarContent'
import { addCalendarDays } from './calendarDates'
beforeEach(() => vi.stubGlobal('indexedDB', new IDBFactory()))

describe('complete day provider with existing offline workflow', () => {
  const day = { date: '2026-10-07', icons: [] } as unknown as CalendarDay
  const repository = { putCalendarDay: vi.fn(), getCalendarDay: vi.fn() } as unknown as DailyContentRepository
  it('opens the real Kalendar day without storing protected replies indefinitely', async () => {
    const bible = { getCalendarDay: vi.fn() } as unknown as BibleApi
    const primary = { getCalendarDay: vi.fn(async () => day) }
    expect(await createCalendarContentService(primary, bible, repository).openCalendarDay('2026-10-07', 'cu')).toEqual({ data: day, offline: false })
    expect(primary.getCalendarDay).toHaveBeenCalledWith('2026-10-07', 'cu')
    expect(bible.getCalendarDay).not.toHaveBeenCalled()
    expect(repository.putCalendarDay).not.toHaveBeenCalled()
  })
  it('does not hide API access, rate limit or contract errors with another calendar', async () => {
    const bible = { getCalendarDay: vi.fn() } as unknown as BibleApi
    for (const error of [new ApiError('http', 'denied', 401), new ApiError('http', 'quota', 429), new ApiError('invalid-response', 'schema')]) {
      const primary = { getCalendarDay: vi.fn(async () => { throw error }) }
      await expect(createCalendarContentService(primary, bible, repository).openCalendarDay('2026-10-07')).rejects.toBe(error)
    }
    expect(bible.getCalendarDay).not.toHaveBeenCalled()
  })
  it('retains the existing Bible API and saved day fallback only for connection failure', async () => {
    const bible = { getCalendarDay: vi.fn(async () => { throw new Error('offline') }) } as unknown as BibleApi
    const storage = { ...repository, getCalendarDay: vi.fn(async () => ({ data: day })) }
    const primary = { getCalendarDay: vi.fn(async () => { throw new ApiError('offline', 'offline') }) }
    expect(await createCalendarContentService(primary, bible, storage).openCalendarDay('2026-10-07')).toEqual({ data: day, offline: true })
  })
})

describe('explicit full calendar download', () => {
  const makeDay = (date: string): CalendarDay => ({ date, old_style_date: date, source: 'kalendar-api', events: [], readings: [], fasting_events: [], icons: [{ id: 1, title: 'Icon', image_url: 'https://example.test/preview', images: [{ url: 'https://example.test/album-original' }] }] }) as CalendarDay
  function fixture() {
    const saved = new Map<string, { data: CalendarDay }>()
    const primary = { getCalendarDay: vi.fn(async (date: string) => makeDay(date)) }
    const bible = { getCalendarDay: vi.fn(), getCalendarService: vi.fn(async () => ({ textLanguage: 'cu-civil', assignments: [], expansions: [] })) } as unknown as BibleApi
    const storage = { getCalendarDay: vi.fn(async (key: string) => saved.get(key)), putCalendarDay: vi.fn(async (value: { key: string; data: CalendarDay }) => { saved.set(value.key, value) }) } as unknown as DailyContentRepository
    const media = { save: vi.fn(async () => undefined), read: vi.fn() }
    return { primary, bible, storage, media, service: createCalendarContentService(primary, bible, storage, media) }
  }
  it('commits all 30 days, downloads each small preview once, never downloads galleries and reopens the complete day offline', async () => {
    const { service, primary, bible, media, storage } = fixture()
    const progress = vi.fn()
    const result = await service.downloadCalendarHorizon('2026-10-08', 30, progress, undefined, 'uk')
    expect(result).toMatchObject({ daysSaved: 30, from: '2026-10-08', to: '2026-11-06', missingAssets: 0, missingServices: 0 })
    expect(progress).toHaveBeenLastCalledWith(30, 30)
    expect(storage.putCalendarDay).toHaveBeenCalledTimes(30)
    expect(media.save).toHaveBeenCalledExactlyOnceWith('https://example.test/preview', true, undefined)
    expect(bible.getCalendarDay).not.toHaveBeenCalled()
    expect(await service.getSavedHorizon('uk')).toEqual(result)
    primary.getCalendarDay.mockRejectedValue(new ApiError('offline', 'offline'))
    expect(await service.openCalendarDay('2026-11-06', 'uk')).toEqual({ data: makeDay('2026-11-06'), offline: true })
  })
  it('reports image failures explicitly instead of claiming an entirely downloaded package', async () => {
    const { service, media } = fixture()
    media.save.mockRejectedValue(new Error('Image timeout'))
    expect(await service.downloadCalendarHorizon('2026-10-08', 2, vi.fn())).toMatchObject({ daysSaved: 2, missingAssets: 2 })
  })
  it('keeps committed days and progress on a later failure, never reports 30/30 early', async () => {
    const { service, primary, storage } = fixture()
    primary.getCalendarDay.mockImplementation(async (date) => { if (date === addCalendarDays('2026-10-08', 2)) throw new ApiError('timeout', 'timeout'); return makeDay(date) })
    const progress = vi.fn()
    await expect(service.downloadCalendarHorizon('2026-10-08', 30, progress)).rejects.toThrow('timeout')
    expect(progress).toHaveBeenLastCalledWith(2, 30)
    expect(storage.putCalendarDay).toHaveBeenCalledTimes(2)
    expect(await service.getSavedHorizon('ru')).toMatchObject({ daysSaved: 2, to: '2026-10-09' })
  })
  it('stops immediately while waiting for a day and does not write unfinished data', async () => {
    const { service, primary, storage } = fixture()
    primary.getCalendarDay.mockImplementation(() => new Promise(() => undefined))
    const controller = new AbortController(), progress = vi.fn()
    const task = service.downloadCalendarHorizon('2026-10-08', 30, progress, controller.signal)
    controller.abort()
    await expect(task).rejects.toMatchObject({ name: 'AbortError' })
    expect(storage.putCalendarDay).not.toHaveBeenCalled()
    expect(progress).not.toHaveBeenCalled()
  })
  it('does not report storage failures as a successful download', async () => {
    const { service, storage } = fixture()
    vi.mocked(storage.putCalendarDay).mockRejectedValue(new DOMException('Full', 'QuotaExceededError'))
    const progress = vi.fn()
    await expect(service.downloadCalendarHorizon('2026-10-08', 30, progress)).rejects.toMatchObject({ name: 'QuotaExceededError' })
    expect(progress).not.toHaveBeenCalled()
  })
})
