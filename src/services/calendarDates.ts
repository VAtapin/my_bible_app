export function calendarDateInTimeZone(date = new Date(), timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

export function addCalendarDays(date: string, amount: number): string {
  const [year, month, day] = date.split('-').map(Number)
  const next = new Date(Date.UTC(year, month - 1, day + amount, 12))
  return next.toISOString().slice(0, 10)
}

export function formatCalendarDate(date: string, locale = 'ru-RU'): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Intl.DateTimeFormat(locale, {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(Date.UTC(year, month - 1, day, 12)))
}

export function formatTodayDate(date: string, oldStyleDate?: string, locale = 'ru-RU'): string {
  function format(value: string, weekday: boolean): string {
    const [year, month, day] = value.split('-').map(Number)
    return new Intl.DateTimeFormat(locale, { timeZone: 'UTC', weekday: weekday ? 'long' : undefined, day: 'numeric', month: 'long' }).format(new Date(Date.UTC(year, month - 1, day, 12)))
  }
  return `${format(date, true)}${oldStyleDate ? ` (${format(oldStyleDate, false)})` : ''}`
}

export type CalendarViewMode = 'day' | 'week' | 'month'
export function isCalendarDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '1900-01-01' || value > '2200-12-31') return false
  const parsed = new Date(`${value}T12:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}
export function calendarPeriodDates(date: string, mode: CalendarViewMode): string[] {
  if (mode === 'day') return [date]
  const current = new Date(`${date}T12:00:00Z`)
  const start = mode === 'week' ? addCalendarDays(date, -((current.getUTCDay() + 6) % 7)) : `${date.slice(0, 8)}01`
  const count = mode === 'week' ? 7 : new Date(Date.UTC(current.getUTCFullYear(), current.getUTCMonth() + 1, 0)).getUTCDate()
  return Array.from({ length: count }, (_, index) => addCalendarDays(start, index))
}
export function moveCalendarPeriod(date: string, amount: number, mode: CalendarViewMode): string {
  if (mode !== 'month') return addCalendarDays(date, amount * (mode === 'week' ? 7 : 1))
  const current = new Date(`${date}T12:00:00Z`)
  const target = new Date(Date.UTC(current.getUTCFullYear(), current.getUTCMonth() + amount, 1, 12))
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate()
  target.setUTCDate(Math.min(current.getUTCDate(), last))
  return target.toISOString().slice(0, 10)
}
