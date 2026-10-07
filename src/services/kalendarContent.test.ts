import { describe, expect, it, vi } from 'vitest'
import { ApiError, type BibleApi } from '@/api/client'
import type { CalendarDay } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { createCalendarContentService } from './kalendarContent'

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
