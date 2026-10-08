import { ApiError } from './client'
import type { CalendarDay, CalendarEvent, CalendarReading } from './contracts'
import { calendarPeriodDates } from '@/services/calendarDates'
import { readCalendarState } from '@/offline/calendarMedia'

export interface CalendarGridDay {
  date: string
  oldStyleDate: string
  weekday: number
  dayStyle: { rank: string; color: string; fontWeight: number }
  foodLabel: string
  fastingColor: string
  events: KalendarEvent[]
}
interface KalendarEvent {
  id: string
  title: string
  typeCode: number
  category: string
  priority?: number
  isIconCommemoration?: boolean
  description?: string | null
  typikonMark?: { label: string; svgSource: string } | null
  reading?: CalendarReading['reading'] | null
}
interface KalendarDay extends CalendarGridDay {
  pascha: string
  weekdayName: string
  tone?: number | null
  weekAfterPentecost?: number | null
  fasting: { reason: string }
  foodMarkers: { source: string; label: string }[]
  memorialMarkers?: { source: string; label: string }[]
  icons: { id?: number; title: string; imageUrl: string | null; description?: string; credit?: string | null; calendarRank?: { eventId: string } | null; images?: { url: string }[]; dates?: { label: string }[] }[]
}
type Metadata = CalendarDay['metadata']
const record = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const isoDate = (value: unknown): value is string => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)
function isEvent(value: unknown): value is KalendarEvent {
  return record(value) && typeof value.id === 'string' && typeof value.title === 'string'
    && typeof value.typeCode === 'number' && typeof value.category === 'string'
    && (value.typikonMark == null || (record(value.typikonMark) && typeof value.typikonMark.label === 'string' && typeof value.typikonMark.svgSource === 'string'))
    && (value.reading == null || (record(value.reading) && typeof value.reading.schemaVersion === 'number'
      && typeof value.reading.parseStatus === 'string' && Array.isArray(value.reading.passages)
      && value.reading.passages.every((passage) => record(passage) && typeof passage.book === 'string'
        && record(passage.start) && Number.isInteger(passage.start.chapter) && (passage.start.verse === null || Number.isInteger(passage.start.verse))
        && record(passage.end) && Number.isInteger(passage.end.chapter) && (passage.end.verse === null || Number.isInteger(passage.end.verse)))))
}
function isGridDay(value: unknown): value is CalendarGridDay {
  return record(value) && isoDate(value.date) && isoDate(value.oldStyleDate)
    && Number.isInteger(value.weekday) && Number(value.weekday) >= 0 && Number(value.weekday) <= 6
    && record(value.dayStyle) && typeof value.dayStyle.rank === 'string'
    && typeof value.dayStyle.color === 'string' && /^#[\da-f]{6}$/i.test(value.dayStyle.color)
    && typeof value.dayStyle.fontWeight === 'number'
    && typeof value.foodLabel === 'string' && typeof value.fastingColor === 'string'
    && Array.isArray(value.events) && value.events.every(isEvent)
}
function isDay(value: unknown): value is KalendarDay {
  return isGridDay(value) && record(value) && isoDate(value.pascha) && typeof value.weekdayName === 'string'
    && record(value.fasting) && typeof value.fasting.reason === 'string'
    && Array.isArray(value.foodMarkers) && value.foodMarkers.every((item) => record(item) && typeof item.source === 'string' && typeof item.label === 'string')
    && Array.isArray(value.icons) && value.icons.every((item) => record(item) && typeof item.title === 'string' && (item.imageUrl === null || typeof item.imageUrl === 'string'))
}

/** Render the API's own assets, never inline untrusted SVG/HTML or guess a sign. */
export function calendarAssetUrl(source: string | undefined, baseUrl: string): string | undefined {
  if (!source) return undefined
  try {
    const base = new URL(baseUrl)
    const url = new URL(source, base)
    return url.origin === base.origin && /^\/assets\/(?:typikon|markers)\/[a-zA-Z0-9_./-]+\.(?:svg|png|webp)$/u.test(url.pathname) && !url.search ? url.href : undefined
  } catch { return undefined }
}

export function primaryTypikonMark(day: CalendarGridDay | undefined) {
  return day?.events.filter((event) => event.category === 'commemoration' && event.typikonMark)
    .slice().sort((left, right) => left.typeCode - right.typeCode)[0]?.typikonMark
}

export function createKalendarApi({ baseUrl, fetcher = fetch, timeoutMs = 15_000, now = Date.now }: { baseUrl: string; fetcher?: typeof fetch; timeoutMs?: number; now?: () => number }) {
  // The documented public read-only client identifier is NOT an API key.
  // No demo endpoint, forged Referer, credential or calendar calculation is used.
  const cache = new Map<string, { expires: number; value: unknown }>()
  const pending = new Map<string, Promise<unknown>>()
  async function request(path: string, params: Record<string, string>, validate: (value: Record<string, unknown>) => boolean): Promise<unknown> {
    const url = `${baseUrl.replace(/\/+$/, '')}/${path}?${new URLSearchParams(params)}`
    const cached = cache.get(url)
    if (cached && cached.expires > now()) return cached.value
    if (pending.has(url)) return pending.get(url)!
    const task = (async () => {
      const controller = new AbortController()
      const timer = setTimeout(() => controller.abort(), timeoutMs)
      try {
        const response = await fetcher(url, { credentials: 'omit', headers: { Accept: 'application/json', 'X-Calendar-Client': 'orthocal-wordpress' }, signal: controller.signal })
        if (!response.ok) throw new ApiError('http', `Kalendar API: HTTP ${response.status}`, response.status)
        let value: unknown
        try { value = await response.json() } catch { throw new ApiError('invalid-response', 'Kalendar API: invalid JSON') }
        if (!record(value) || !record(value.metadata) || !validate(value)) throw new ApiError('invalid-response', 'Kalendar API: invalid response')
        if (cache.size >= 24) cache.delete(cache.keys().next().value!)
        cache.set(url, { expires: now() + 300_000, value })
        return value
      } catch (error) {
        if (error instanceof ApiError) throw error
        throw new ApiError(controller.signal.aborted ? 'timeout' : 'offline', 'Kalendar API: connection failed')
      } finally { clearTimeout(timer) }
    })()
    pending.set(url, task)
    try { return await task } finally { pending.delete(url) }
  }
  return {
    async getMonth(date: string, language = 'ru'): Promise<CalendarGridDay[]> {
      const expectedDates = calendarPeriodDates(date, 'month')
      let value: unknown
      try {
        value = await request('month', { year: date.slice(0, 4), month: String(Number(date.slice(5, 7))), lang: language, profile: 'typikon-strict', view: 'summary' }, (value) => Array.isArray(value.days)
          && value.days.length === expectedDates.length && value.days.every((day, index) => isGridDay(day) && day.date === expectedDates[index]))
      } catch (error) {
        if (!(error instanceof ApiError) || !['offline', 'timeout'].includes(error.kind)) throw error
        const saved = await readCalendarState<CalendarGridDay[]>(`calendar-month:${language}:${date.slice(0, 7)}`).catch(() => undefined)
        if (!saved || saved.length !== expectedDates.length || !saved.every((day, index) => isGridDay(day) && day.date === expectedDates[index])) throw error
        value = { days: saved }
      }
      if (!record(value) || !Array.isArray(value.days) || !value.days.length || !value.days.every(isGridDay)
        || value.days.some((day) => !day.date.startsWith(date.slice(0, 7)))) throw new ApiError('invalid-response', 'Kalendar API: invalid month')
      return value.days
    },
    async getCalendarDay(date: string, language = 'ru'): Promise<CalendarDay> {
      const value = await request('day', { date, lang: language, profile: 'typikon-strict' }, (value) => isDay(value.day) && value.day.date === date)
      if (!record(value) || !isDay(value.day) || value.day.date !== date) throw new ApiError('invalid-response', 'Kalendar API: invalid day')
      const day = value.day
      const events: CalendarEvent[] = day.events.filter((event) => event.category === 'commemoration').map((event) => ({
        id: event.id, name: event.title, is_icon_commemoration: event.isIconCommemoration ?? false, is_fasting: false,
        type_code: event.typeCode, description: event.description,
        typikon_mark: event.typikonMark && calendarAssetUrl(event.typikonMark.svgSource, baseUrl)
          ? { label: event.typikonMark.label, image_url: calendarAssetUrl(event.typikonMark.svgSource, baseUrl)! } : null,
        type: event.typikonMark ? { code: event.typikonMark.svgSource, name: event.typikonMark.label } : null,
      }))
      return {
        date, old_style_date: day.oldStyleDate, pascha_date: day.pascha, liturgical_period: day.weekdayName,
        source: 'kalendar-api', metadata: value.metadata as unknown as Metadata,
        day_style: day.dayStyle, tone: day.tone, week_after_pentecost: day.weekAfterPentecost,
        food: { label: day.foodLabel, reason: day.fasting.reason, color: day.fastingColor,
          image_url: day.foodMarkers.map((marker) => calendarAssetUrl(marker.source, baseUrl)).find(Boolean) },
        memorial_markers: (day.memorialMarkers ?? []).slice(0, 1).flatMap((marker) => {
          const image_url = calendarAssetUrl(marker.source, baseUrl)
          return image_url ? [{ label: marker.label, image_url }] : []
        }),
        events, fasting_events: [],
        other_events: day.events.filter((event) => !['commemoration', 'scripture-reading'].includes(event.category)).map((event) => ({ id: event.id, name: event.title, category: event.category, description: event.description })),
        readings: day.events.filter((event) => event.category === 'scripture-reading').map((event) => ({
          id: event.id, type: event.category, title: event.title, display_ref: event.title, passage_ref: '', date_rule_type: '', reading: event.reading ?? undefined,
        })),
        // Keep the exact order chosen by Kalendar's calendarRank, not title guesses.
        icons: day.icons.filter((icon) => Number.isInteger(icon.id) && icon.imageUrl && /^https?:\/\//u.test(icon.imageUrl)).map((icon) => ({
          id: icon.id!, title: icon.title, image_url: icon.imageUrl, description: icon.description, credit: icon.credit,
          images: (icon.images ?? []).filter((image) => typeof image.url === 'string' && /^https?:\/\//u.test(image.url)),
          dates: (icon.dates ?? []).filter((date) => typeof date.label === 'string'),
          calendar_record_ids: icon.calendarRank ? [icon.calendarRank.eventId] : [],
        })),
      }
    },
  }
}
