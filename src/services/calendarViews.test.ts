import { describe, expect, it } from 'vitest'
import { calendarPeriodDates, moveCalendarPeriod, isCalendarDate } from './calendarDates'
import { rankedCalendarIcons } from './calendarIcons'
import type { CalendarDay } from '@/api/contracts'

describe('calendar periods and icons', () => {
  it('accepts only actual civil dates within the existing API range', () => {
    for (const date of ['1900-01-01', '2200-12-31', '2028-02-29']) expect(isCalendarDate(date)).toBe(true)
    for (const date of ['2026-02-29', '2026-13-01', '2026-10-99', '1899-12-31', '2201-01-01', undefined, ['2026-10-07']]) expect(isCalendarDate(date)).toBe(false)
  })
  it('starts weeks on Monday and crosses year boundaries', () => {
    expect(calendarPeriodDates('2027-01-01', 'week')).toEqual(['2026-12-28', '2026-12-29', '2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02', '2027-01-03'])
    expect(calendarPeriodDates('2028-02-10', 'month')).toHaveLength(29)
    expect(calendarPeriodDates('2027-02-10', 'month')).toHaveLength(28)
    expect(moveCalendarPeriod('2026-01-31', 1, 'month')).toBe('2026-02-28')
    expect(moveCalendarPeriod('2026-10-07', -1, 'week')).toBe('2026-09-30')
  })
  it('chooses icons by linked events in API precedence order, not icon array order or title guessing', () => {
    const day = { date: '2026-10-07', events: [{ id: 'feast:2026-10-07' }, { id: 'saint:2026-10-07' }], icons: [
      { id: 1, title: 'Saint', image_url: '/saint', calendar_record_ids: ['saint'] },
      { id: 2, title: 'Feast', image_url: '/feast', calendar_record_ids: ['feast'] },
      { id: 3, title: 'No image', image_url: null, calendar_record_ids: ['feast'] },
    ] } as CalendarDay
    expect(rankedCalendarIcons(day).map((icon) => icon.id)).toEqual([2, 1])
    expect(day.icons?.[0]?.id).toBe(1)
  })
})
