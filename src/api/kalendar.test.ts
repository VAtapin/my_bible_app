import { describe, expect, it, vi } from 'vitest'
import { calendarAssetUrl, createKalendarApi, primaryTypikonMark } from './kalendar'
import { calendarPeriodDates } from '@/services/calendarDates'
import { rankedCalendarIcons } from '@/services/calendarIcons'
import { calendarEvents, calendarReadingLink } from '@/services/calendarPresentation'

const baseUrl = 'https://calendar.example/api/v1/calendar'
const mark = { label: 'Шестеричная служба', svgSource: '/assets/typikon/six-stichera.svg' }
const event = { id: 'memory:2026-10-07', title: 'Память', typeCode: 6, category: 'commemoration', typikonMark: mark }
const gridDay = (date: string) => ({ date, oldStyleDate: '2026-09-24', weekday: 3, dayStyle: { rank: 'ordinary', color: '#17201d', fontWeight: 700 }, foodLabel: 'сухоядение', fastingColor: '#6c613a', events: [event] })
const metadata = { apiVersion: '1.0.0', language: 'ru', fastingProfileId: 'typikon-strict' }
const sourceDay = { ...gridDay('2026-10-07'), pascha: '2026-04-12', weekdayName: 'Среда', tone: 1, weekAfterPentecost: 19,
  fasting: { reason: 'Сухоядение (Типикон, гл. 33)' }, foodMarkers: [{ label: 'Орнаментальные', source: '/assets/markers/ornamental/dry-eating.png' }],
  icons: [
    { id: 299, title: 'Богородица', imageUrl: 'https://bible.example/icons/299', calendarRank: null, description: 'Описание', images: [{ url: 'https://bible.example/icons/300' }], dates: [{ label: '24 сентября' }] },
    { id: 10, title: 'Святой', imageUrl: 'https://bible.example/icons/10', calendarRank: { eventId: event.id } },
  ], events: [event, ...Array.from({ length: 7 }, (_, index) => ({ ...event, id: `minor-${index}`, typeCode: 18, typikonMark: null })),
    { ...event, id: 'reading', title: 'Флп.1:12-20', category: 'scripture-reading', typeCode: 204, typikonMark: null,
      reading: { schemaVersion: 1, parseStatus: 'parsed', passages: [{ book: 'Phil', start: { chapter: 1, verse: 12 }, end: { chapter: 1, verse: 20 } }] } },
    { ...event, id: 'marriage', title: 'Венчания не совершаются', category: 'marriage-rule', typeCode: 20, typikonMark: null },
  ],
}
const month = { metadata, days: calendarPeriodDates('2026-10-07', 'month').map(gridDay) }
const response = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })

describe('existing Kalendar API integration', () => {
  it('uses the documented public read-only identifier, summary month and separate configured origin', async () => {
    const fetcher = vi.fn(async () => response(month))
    const days = await createKalendarApi({ baseUrl, fetcher }).getMonth('2026-10-07', 'de')
    expect(days).toHaveLength(31)
    const [url, options] = fetcher.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toContain('/month?year=2026&month=10&lang=de&profile=typikon-strict&view=summary')
    expect(options.headers).toEqual({ Accept: 'application/json', 'X-Calendar-Client': 'orthocal-wordpress' })
    expect(options.credentials).toBe('omit')
    expect(url).not.toContain('demo')
  })
  it('preserves every memory, sign, post, icon order, icon image and reading from the API', async () => {
    const data = await createKalendarApi({ baseUrl, fetcher: vi.fn(async () => response({ metadata, day: sourceDay })) }).getCalendarDay('2026-10-07')
    expect(data.events).toHaveLength(8)
    expect(calendarEvents(data.events, 'all')).toHaveLength(8)
    expect(data.other_events).toEqual([{ id: 'marriage', name: 'Венчания не совершаются', category: 'marriage-rule', description: undefined }])
    expect(data.events[0].typikon_mark?.image_url).toBe('https://calendar.example/assets/typikon/six-stichera.svg')
    expect(data.food?.image_url).toBe('https://calendar.example/assets/markers/ornamental/dry-eating.png')
    expect(data.food?.reason).toContain('гл. 33')
    expect(rankedCalendarIcons(data).map((icon) => icon.id)).toEqual([299, 10])
    expect(data.icons?.[0].images).toHaveLength(1)
    expect(data.icons?.[0].dates?.[0].label).toBe('24 сентября')
    expect(calendarReadingLink(data.readings[0])).toEqual({ path: '/reader', query: { book: 'philippians', chapter: '1', verse: '12' } })
  })
  it('uses the API day style verbatim and the highest supplied typikon sign, never guesses holidays by name', async () => {
    const days = structuredClone(month)
    days.days[0].dayStyle = { rank: 'great-feast', color: '#a12b2b', fontWeight: 700 }
    days.days[1].events = [{ ...event, title: 'Пасха в названии памяти', typikonMark: null }]
    const result = await createKalendarApi({ baseUrl, fetcher: vi.fn(async () => response(days)) }).getMonth('2026-10-07')
    expect(result[0].dayStyle.rank).toBe('great-feast')
    expect(result[1].dayStyle.rank).toBe('ordinary')
    expect(primaryTypikonMark({ ...result[0], events: [event, { ...event, typeCode: 2, typikonMark: { label: 'Великий праздник', svgSource: '/assets/typikon/great.svg' } }] })?.label).toBe('Великий праздник')
    expect(primaryTypikonMark(result[1])).toBeUndefined()
  })
  it('deduplicates requests for the same month and expires data after five minutes without stale fallback', async () => {
    let clock = 0
    const fetcher = vi.fn(async () => response(month))
    const api = createKalendarApi({ baseUrl, fetcher, now: () => clock })
    await Promise.all([api.getMonth('2026-10-07'), api.getMonth('2026-10-10')])
    expect(fetcher).toHaveBeenCalledTimes(1)
    clock = 300_000
    fetcher.mockImplementation(async () => response({ error: 'revoked' }, 403))
    await expect(api.getMonth('2026-10-07')).rejects.toMatchObject({ status: 403 })
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
  it('rejects incomplete or wrong month responses and does not cache invalid results', async () => {
    const fetcher = vi.fn(async () => response({ ...month, days: month.days.slice(0, 1) }))
    const api = createKalendarApi({ baseUrl, fetcher })
    await expect(api.getMonth('2026-10-07')).rejects.toMatchObject({ kind: 'invalid-response' })
    fetcher.mockImplementation(async () => response(month))
    expect(await api.getMonth('2026-10-07')).toHaveLength(31)
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
  it('rejects a wrong day and malformed reading rather than linking it to an unrelated verse', async () => {
    const api = createKalendarApi({ baseUrl, fetcher: vi.fn(async () => response({ metadata, day: { ...sourceDay, date: '2026-10-08' } })) })
    await expect(api.getCalendarDay('2026-10-07')).rejects.toMatchObject({ kind: 'invalid-response' })
    const invalid = createKalendarApi({ baseUrl, fetcher: vi.fn(async () => response({ metadata, day: { ...sourceDay, events: [{ ...event, reading: { passages: [{}] } }] } })) })
    await expect(invalid.getCalendarDay('2026-10-07')).rejects.toMatchObject({ kind: 'invalid-response' })
  })
  it('treats invalid JSON as an invalid response, not an offline condition', async () => {
    await expect(createKalendarApi({ baseUrl, fetcher: vi.fn(async () => new Response('<html>')) }).getCalendarDay('2026-10-07')).rejects.toMatchObject({ kind: 'invalid-response' })
  })
  it('allows only the calendar origin and actual marker asset paths', () => {
    expect(calendarAssetUrl('/assets/typikon/great.svg', baseUrl)).toBe('https://calendar.example/assets/typikon/great.svg')
    for (const source of ['javascript:alert(1)', 'https://other.example/assets/typikon/great.svg', '/api/private.svg', '/assets/typikon/great.svg?key=private']) expect(calendarAssetUrl(source, baseUrl)).toBeUndefined()
  })
})
