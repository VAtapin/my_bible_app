import type { CalendarEvent, CalendarReading } from '@/api/contracts'
import type { CalendarLevel } from '@/profile/configuration'
import { readerTarget } from './bibleReferences'

export function calendarEvents(events: CalendarEvent[], level: CalendarLevel): CalendarEvent[] {
  const values = events.filter((event) => !event.is_fasting)
  return level === 'major' ? values.filter((event) => event.type || event.is_icon_commemoration) : values
}

export function fastingNote(event: CalendarEvent): string {
  const metadata = event.metadata
  return metadata && !Array.isArray(metadata) && typeof metadata.meal_note === 'string' ? metadata.meal_note : event.name
}

export function calendarReadingLink(reading: CalendarReading) {
  const passage = reading.reading?.passages[0]
  const target = passage && readerTarget(passage.book, passage.start.chapter)
  return target ? { path: '/reader', query: { ...target, ...(passage?.start.verse ? { verse: String(passage.start.verse) } : {}) } } : undefined
}
