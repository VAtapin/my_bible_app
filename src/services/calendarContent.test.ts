import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IDBFactory } from 'fake-indexeddb'
import { ApiError, type BibleApi } from '@/api/client'
import type { CalendarDay } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { createCalendarContentService, waitForCalendarRequest } from './calendarContent'
import { addCalendarDays } from './calendarDates'
import { readCalendarState } from '@/offline/calendarMedia'
beforeEach(() => vi.stubGlobal('indexedDB', new IDBFactory()))
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

describe('complete day provider with existing offline workflow', () => {
  const day = { date: '2026-10-07', source: 'bible-desktop-calendar-engine', icons: [] } as unknown as CalendarDay
  const repository = { putCalendarDay: vi.fn(), getCalendarDay: vi.fn() } as unknown as DailyContentRepository
  it('opens the BibleDesktop day without storing protected replies indefinitely', async () => {
    const primary = { getCalendarDay: vi.fn(async () => day) }
    expect(await createCalendarContentService(primary as unknown as BibleApi, repository).openCalendarDay('2026-10-07', 'cu')).toEqual({ data: day, offline: false })
    expect(primary.getCalendarDay).toHaveBeenCalledWith('2026-10-07', 'cu')
    expect(repository.putCalendarDay).not.toHaveBeenCalled()
  })
  it('does not hide API access, rate limit or contract errors with another calendar', async () => {
    for (const error of [new ApiError('http', 'denied', 401), new ApiError('http', 'quota', 429), new ApiError('invalid-response', 'schema')]) {
      const primary = { getCalendarDay: vi.fn(async () => { throw error }) }
      await expect(createCalendarContentService(primary as unknown as BibleApi, repository).openCalendarDay('2026-10-07')).rejects.toBe(error)
    }
  })
  it('uses only a saved BibleDesktop day on connection failure', async () => {
    const storage = { ...repository, getCalendarDay: vi.fn(async () => ({ data: day })) }
    const primary = { getCalendarDay: vi.fn(async () => { throw new ApiError('offline', 'offline') }) }
    expect(await createCalendarContentService(primary as unknown as BibleApi, storage).openCalendarDay('2026-10-07')).toEqual({ data: day, offline: true })
  })
  it('does not substitute a saved external-source day after switching API', async () => {
    const error = new ApiError('offline', 'offline')
    const api = { getCalendarDay: vi.fn(async () => { throw error }) } as unknown as BibleApi
    const storage = { ...repository, getCalendarDay: vi.fn(async () => ({ data: { ...day, source: 'retired-external-source' } })) }
    await expect(createCalendarContentService(api, storage).openCalendarDay('2026-10-07')).rejects.toBe(error)
  })
})

describe('explicit full calendar download', () => {
  const makeDay = (date: string): CalendarDay => ({ date, old_style_date: date, source: 'bible-desktop-calendar-engine', events: [], readings: [], fasting_events: [], icons: [{ id: 1, title: 'Icon', image_url: 'https://example.test/preview', images: [{ url: 'https://example.test/album-original' }] }] }) as CalendarDay
  function fixture() {
    const saved = new Map<string, { data: CalendarDay }>()
    const primary = { getCalendarDay: vi.fn(async (date: string) => makeDay(date)), getCalendarService: vi.fn(async () => ({ date: '2026-10-08', textLanguage: 'cu-civil', assignments: [], expansions: [] })) }
    const storage = { getCalendarDay: vi.fn(async (key: string) => saved.get(key)), putCalendarDay: vi.fn(async (value: { key: string; data: CalendarDay }) => { saved.set(value.key, value) }) } as unknown as DailyContentRepository
    const media = { save: vi.fn(async () => undefined), read: vi.fn() }
    const wait = vi.fn(async () => undefined)
    return { primary, storage, media, wait, service: createCalendarContentService(primary, storage, media, wait) }
  }
  it('commits all 30 days, downloads each small preview once, never downloads galleries and reopens the complete day offline', async () => {
    const { service, primary, media, storage } = fixture()
    const progress = vi.fn()
    const result = await service.downloadCalendarHorizon('2026-10-08', 30, progress, undefined, 'uk')
    expect(result).toMatchObject({ daysSaved: 30, from: '2026-10-08', to: '2026-11-06', missingAssets: 0, missingServices: 0 })
    expect(progress).toHaveBeenLastCalledWith(30, 30)
    expect(storage.putCalendarDay).toHaveBeenCalledTimes(30)
    expect(media.save).toHaveBeenCalledExactlyOnceWith('https://example.test/preview', true, undefined)
    expect(await service.getSavedHorizon('uk')).toEqual(result)
    primary.getCalendarDay.mockRejectedValue(new ApiError('offline', 'offline'))
    expect(await service.openCalendarDay('2026-11-06', 'uk')).toEqual({ data: makeDay('2026-11-06'), offline: true })
  })
  it('reports image failures explicitly instead of claiming an entirely downloaded package', async () => {
    const { service, media } = fixture()
    media.save.mockRejectedValue(new Error('Image timeout'))
    expect(await service.downloadCalendarHorizon('2026-10-08', 2, vi.fn())).toMatchObject({ daysSaved: 2, missingAssets: 2 })
  })
  it('passes the supplied small preview separately from the original offline lookup key', async () => {
    const { service, primary, media } = fixture()
    primary.getCalendarDay.mockImplementation(async (date) => {
      const data = makeDay(date)
      data.icons![0]!.imagePreviewUrl = 'https://example.test/api/calendar/icons/1/images/2?preview=1'
      return data
    })
    await service.downloadCalendarHorizon('2026-10-08', 1, vi.fn())
    expect(media.save).toHaveBeenCalledExactlyOnceWith('https://example.test/preview', true, undefined, 'https://example.test/api/calendar/icons/1/images/2?preview=1')
  })
  it('saves all month grid signs, even when absent in the downloaded day, and deduplicates shared URLs', async () => {
    const { primary, storage, media, wait } = fixture()
    const sign = { label: 'Vigil', svgSource: 'https://bible.example/assets/typikon/vigil.svg' }
    const month = [{ date: '2026-10-01', oldStyleDate: '2026-09-18', weekday: 4, dayStyle: { rank: 'ordinary', color: '#000', fontWeight: 400 }, foodLabel: '', fastingColor: '#000', events: [{ id: 'feast', title: 'Feast', typeCode: 4, category: 'commemoration', typikonMark: sign }, { id: 'second', title: 'Second', typeCode: 4, category: 'commemoration', typikonMark: sign }] }]
    const api = { ...primary, getCalendarMonth: vi.fn(async () => month) }
    const service = createCalendarContentService(api, storage, media, wait)
    await service.downloadCalendarHorizon('2026-10-08', 1, vi.fn())
    expect(media.save).toHaveBeenCalledWith(sign.svgSource, false, undefined)
    expect(media.save.mock.calls.filter(([url]) => url === sign.svgSource)).toHaveLength(1)
    expect(await readCalendarState('bible-calendar-month:ru:2026-10')).toEqual(month)
    expect(await readCalendarState('bible-calendar-horizon:ru')).toMatchObject({ daysSaved: 1, missingAssets: 0 })
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
  it('paces the download and respects Retry-After instead of changing API or serving stale data', async () => {
    const { service, primary, wait, storage } = fixture()
    primary.getCalendarDay.mockRejectedValueOnce(new ApiError('http', 'quota', 429, 12_000))
    const result = await service.downloadCalendarHorizon('2026-10-08', 1, vi.fn())
    expect(wait).toHaveBeenCalledWith(12_000, undefined)
    expect(primary.getCalendarDay).toHaveBeenCalledTimes(2)
    expect(storage.putCalendarDay).toHaveBeenCalledTimes(1)
    expect(result.daysSaved).toBe(1)
    expect(wait.mock.calls.some(([ms]) => ms > 0 && ms <= 2100)).toBe(true)
  })
  it('does not repeatedly retry a rate limit', async () => {
    const { service, primary, storage } = fixture()
    primary.getCalendarDay.mockRejectedValue(new ApiError('http', 'quota', 429))
    await expect(service.downloadCalendarHorizon('2026-10-08', 1, vi.fn())).rejects.toMatchObject({ status: 429 })
    expect(primary.getCalendarDay).toHaveBeenCalledTimes(2)
    expect(storage.putCalendarDay).not.toHaveBeenCalled()
  })
})

it('cancels a paced wait immediately', async () => {
  vi.useFakeTimers()
  const controller = new AbortController()
  const task = waitForCalendarRequest(61_000, controller.signal)
  controller.abort()
  await expect(task).rejects.toMatchObject({ name: 'AbortError' })
  expect(vi.getTimerCount()).toBe(0)
})
