import { describe, expect, it } from 'vitest'
import { addCalendarDays, calendarDateInTimeZone } from './calendarDates'

describe('calendar dates', () => {
  it('calculates the church day in the requested timezone', () => {
    const instant = new Date('2026-03-28T23:30:00.000Z')
    expect(calendarDateInTimeZone(instant, 'Europe/Berlin')).toBe('2026-03-29')
    expect(calendarDateInTimeZone(instant, 'America/New_York')).toBe('2026-03-28')
  })

  it('adds civil days safely across daylight-saving changes', () => {
    expect(addCalendarDays('2026-03-28', 1)).toBe('2026-03-29')
    expect(addCalendarDays('2026-10-24', 2)).toBe('2026-10-26')
  })
})
