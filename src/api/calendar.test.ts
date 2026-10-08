import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { IDBFactory } from 'fake-indexeddb'
import { ApiError, createBibleApi } from './client'
import { calendarAssetUrl, isCalendarMonth, normalizeCalendarAssets, primaryTypikonMark, type CalendarGridDay } from './calendar'
import type { CalendarDay } from './contracts'
import { calendarPeriodDates } from '@/services/calendarDates'
import { writeCalendarState } from '@/offline/calendarMedia'

beforeEach(() => vi.stubGlobal('indexedDB', new IDBFactory()))
afterEach(() => vi.unstubAllGlobals())
const month = (date = '2026-10-08'): CalendarGridDay[] => calendarPeriodDates(date, 'month').map((date) => ({ date, oldStyleDate: date, weekday: 0, dayStyle: { rank: 'ordinary', color: '#000', fontWeight: 400 }, foodLabel: '', fastingColor: '#000', events: [] }))

describe('single BibleDesktop calendar API', () => {
  it('uses only the supplied same-origin preview for the matching icon, preserving the original gallery URL', () => {
    const image_url = 'https://bible.example/storage/calendar-icons/original.jpg'
    const icon = { id: 310, title: 'Icon', image_url }
    const urls = ['/api/calendar/icons/310/images/3548?preview=1', 'https://other.example/api/calendar/icons/310/images/3548?preview=1', '/api/calendar/icons/999/images/3548?preview=1', image_url]
    const normalized = normalizeCalendarAssets({ events: [], icons: urls.map((imagePreviewUrl) => ({ ...icon, imagePreviewUrl })) } as unknown as CalendarDay, 'https://bible.example/api/')
    expect(normalized.icons?.map((item) => item.imagePreviewUrl)).toEqual(['https://bible.example/api/calendar/icons/310/images/3548?preview=1', undefined, undefined, undefined])
    expect(normalized.icons?.every((item) => item.image_url === image_url)).toBe(true)
  })
  it('requests a complete month only from the configured BibleDesktop API', async () => {
    const days = month()
    const fetcher = vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: days })))
    const api = createBibleApi({ baseUrl: 'https://bible.example/api/', fetcher })
    expect(await api.getCalendarMonth('2026-10-08', 'uk')).toEqual(days)
    expect(fetcher).toHaveBeenCalledExactlyOnceWith('https://bible.example/api/calendar/month?year=2026&month=10&lang=uk&profile=typikon-strict', expect.objectContaining({ credentials: 'omit', headers: { Accept: 'application/json' } }))
  })
  it('normalizes month sign addresses so offline grid images use the same cache key', async () => {
    const days = month()
    days[0]!.events = [{ id: 'feast', title: 'Feast', typeCode: 4, category: 'commemoration', typikonMark: { label: 'Vigil', svgSource: '/assets/typikon/vigil.svg' } }]
    const api = createBibleApi({ baseUrl: 'https://bible.example/api', fetcher: vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: days }))) })
    expect((await api.getCalendarMonth('2026-10-08'))[0]?.events[0]?.typikonMark?.svgSource).toBe('https://bible.example/assets/typikon/vigil.svg')
  })
  it('accepts leap February and rejects missing, wrong and duplicate days', () => {
    const days = month('2028-02-08')
    expect(days).toHaveLength(29)
    expect(isCalendarMonth(days, '2028-02-08')).toBe(true)
    expect(isCalendarMonth(days.slice(1), '2028-02-08')).toBe(false)
    expect(isCalendarMonth([...days.slice(0, -1), days[0]], '2028-02-08')).toBe(false)
    expect(isCalendarMonth(days, '2026-02-08')).toBe(false)
  })
  it('opens explicitly saved BibleDesktop months only on connection failure', async () => {
    await writeCalendarState('bible-calendar-month:ru:2026-10', month())
    const api = createBibleApi({ baseUrl: 'https://bible.example/api', fetcher: vi.fn<typeof fetch>().mockRejectedValue(new TypeError('offline')) })
    expect(await api.getCalendarMonth('2026-10-08')).toEqual(month())
    await expect(api.getCalendarMonth('2026-10-08', 'de')).rejects.toMatchObject({ kind: 'offline' })
  })
  it.each([401, 403, 429])('does not mask HTTP %s with a saved month', async (status) => {
    await writeCalendarState('bible-calendar-month:ru:2026-10', month())
    const api = createBibleApi({ baseUrl: 'https://bible.example/api', fetcher: vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status })) })
    await expect(api.getCalendarMonth('2026-10-08')).rejects.toMatchObject({ kind: 'http', status })
  })
  it('does not mask invalid responses with saved data', async () => {
    await writeCalendarState('bible-calendar-month:ru:2026-10', month())
    const api = createBibleApi({ baseUrl: 'https://bible.example/api', fetcher: vi.fn<typeof fetch>().mockResolvedValue(new Response(JSON.stringify({ data: [] }))) })
    await expect(api.getCalendarMonth('2026-10-08')).rejects.toMatchObject({ kind: 'invalid-response' })
  })
  it('does not misclassify malformed JSON as an offline connection', async () => {
    await writeCalendarState('bible-calendar-month:ru:2026-10', month())
    const api = createBibleApi({ baseUrl: 'https://bible.example/api', fetcher: vi.fn<typeof fetch>().mockResolvedValue(new Response('<html>error</html>')) })
    await expect(api.getCalendarMonth('2026-10-08')).rejects.toMatchObject({ kind: 'invalid-response' })
  })
  it('exposes Retry-After for paced offline downloads', async () => {
    const api = createBibleApi({ baseUrl: 'https://bible.example/api', fetcher: vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 429, headers: { 'Retry-After': '12' } })) })
    await expect(api.getCalendarMonth('2026-10-08')).rejects.toMatchObject<ApiError>({ status: 429, retryAfterMs: 12_000 })
  })
  it('loads only marker assets on the BibleDesktop origin', () => {
    const base = 'https://bible.example/api'
    expect(calendarAssetUrl('/assets/typikon/vigil.svg', base)).toBe('https://bible.example/assets/typikon/vigil.svg')
    expect(calendarAssetUrl('/assets/markers/ornamental/dry-eating.png', base)).toBe('https://bible.example/assets/markers/ornamental/dry-eating.png')
    expect(calendarAssetUrl('https://other.example/assets/typikon/vigil.svg', base)).toBeUndefined()
    expect(calendarAssetUrl('/assets/../secret.svg', base)).toBeUndefined()
  })
  it('resolves day Typikon and food paths against BibleDesktop rather than the app host', () => {
    const day = { events: [{ id: 'saint', name: 'Saint', is_icon_commemoration: false, is_fasting: false, typikon_mark: { label: 'Vigil', image_url: '/assets/typikon/vigil.svg' } }], food: { label: 'Food', reason: null, color: null, image_url: '/assets/markers/minimal-dark/dry-eating.png' }, memorial_markers: [{ label: 'Memorial', image_url: '/assets/markers/minimal-dark/memorial.png' }] } as CalendarDay
    const normalized = normalizeCalendarAssets(day, 'https://bible.example/api')
    expect(normalized.events[0]?.typikon_mark?.image_url).toBe('https://bible.example/assets/typikon/vigil.svg')
    expect(normalized.food?.image_url).toBe('https://bible.example/assets/markers/minimal-dark/dry-eating.png')
    expect(normalized.memorial_markers?.[0]?.image_url).toBe('https://bible.example/assets/markers/minimal-dark/memorial.png')
    expect(day.events[0]?.typikon_mark?.image_url).toBe('/assets/typikon/vigil.svg')
  })
  it('selects the existing API rank mark without comparing localized text', () => {
    const day = month()[0]!
    day.events = [{ id: 'lesser', title: 'A', typeCode: 7, category: 'commemoration', typikonMark: { label: 'Lesser', svgSource: '/assets/typikon/six-stichera.svg' } }, { id: 'greater', title: 'Z', typeCode: 4, category: 'commemoration', typikonMark: { label: 'Greater', svgSource: '/assets/typikon/vigil.svg' } }]
    expect(primaryTypikonMark(day)?.label).toBe('Greater')
  })
})
