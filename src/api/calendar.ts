import { calendarPeriodDates } from '@/services/calendarDates'
import type { CalendarDay } from './contracts'

export interface CalendarGridDay {
  date: string
  oldStyleDate: string
  weekday: number
  dayStyle: { rank: string; color: string; fontWeight: number }
  foodLabel: string
  fastingColor: string
  events: { id: string; title: string; typeCode: number; category: string; typikonMark?: { label: string; svgSource: string } | null }[]
}
const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
export function isCalendarMonth(value: unknown, date: string): value is CalendarGridDay[] {
  const dates = calendarPeriodDates(date, 'month')
  return Array.isArray(value) && value.length === dates.length && value.every((day, index) => record(day)
    && day.date === dates[index] && typeof day.oldStyleDate === 'string'
    && Number.isInteger(day.weekday) && Number(day.weekday) >= 0 && Number(day.weekday) <= 6
    && record(day.dayStyle) && typeof day.dayStyle.rank === 'string' && typeof day.dayStyle.color === 'string' && typeof day.dayStyle.fontWeight === 'number'
    && typeof day.foodLabel === 'string' && typeof day.fastingColor === 'string'
    && Array.isArray(day.events) && day.events.every((event) => record(event) && typeof event.id === 'string'
      && typeof event.title === 'string' && typeof event.typeCode === 'number' && typeof event.category === 'string'
      && (event.typikonMark == null || (record(event.typikonMark) && typeof event.typikonMark.label === 'string' && typeof event.typikonMark.svgSource === 'string'))))
}
/** Calendar assets must come from the same BibleDesktop origin as the data. */
export function calendarAssetUrl(source: string | undefined, baseUrl: string): string | undefined {
  if (!source) return undefined
  try {
    const base = new URL(baseUrl), url = new URL(source, base)
    return url.origin === base.origin && /^\/assets\/(?:typikon|markers)\/[a-zA-Z0-9_./-]+\.(?:svg|png|webp)$/u.test(url.pathname) && !url.search ? url.href : undefined
  } catch { return undefined }
}
export function primaryTypikonMark(day: CalendarGridDay | undefined) {
  return day?.events.filter((event) => event.category === 'commemoration' && event.typikonMark)
    .slice().sort((a, b) => a.typeCode - b.typeCode)[0]?.typikonMark
}

export function normalizeCalendarMonthAssets(days: CalendarGridDay[], baseUrl: string): CalendarGridDay[] {
  return days.map((day) => ({ ...day, events: day.events.map((event) => {
    const url = calendarAssetUrl(event.typikonMark?.svgSource, baseUrl)
    return { ...event, typikonMark: url && event.typikonMark ? { ...event.typikonMark, svgSource: url } : null }
  }) }))
}

/** API asset paths are relative to BibleDesktop, never to the frontend host. */
export function normalizeCalendarAssets(day: CalendarDay, baseUrl: string): CalendarDay {
  const marker = (value: { label: string; image_url: string } | null | undefined) => {
    const url = calendarAssetUrl(value?.image_url, baseUrl)
    return url && typeof value?.label === 'string' ? { ...value, image_url: url } : null
  }
  return {
    ...day,
    icons: day.icons?.map((icon) => {
      let imagePreviewUrl: string | undefined
      try {
        const url = new URL(icon.imagePreviewUrl ?? '', baseUrl)
        if (url.origin === new URL(baseUrl).origin && new RegExp(`^/api/calendar/icons/${icon.id}/images/\\d+$`).test(url.pathname)
          && url.search === '?preview=1' && !url.hash) imagePreviewUrl = url.href
      } catch { /* Unknown preview URLs are not substituted for originals. */ }
      return { ...icon, imagePreviewUrl }
    }),
    events: day.events.map((event) => ({ ...event, typikon_mark: marker(event.typikon_mark) })),
    ...(day.food ? { food: { ...day.food, image_url: calendarAssetUrl(day.food.image_url ?? undefined, baseUrl) } } : {}),
    ...(day.memorial_markers ? { memorial_markers: day.memorial_markers.map(marker).filter((value): value is { label: string; image_url: string } => value !== null) } : {}),
  }
}
