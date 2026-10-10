import type { CalendarEvent, CalendarReading } from '@/api/contracts'
import type { CalendarLevel } from '@/profile/configuration'
import { readerTarget } from './bibleReferences'
import type {CalendarGridDay} from '@/api/calendar'

// Actual calendar-engine FASTING_COLORS, not translated meal-title heuristics.
const mealColors=new Set(['#dcebc9','#cce9f3','#f7e5b5','#ecdac7','#e3d5ed','#bfd9de','#f4cfaa','#c9c4d6','#fff3bf','#dedee5'])
export function calendarMealMark(day:Pick<CalendarGridDay,'foodLabel'|'fastingColor'>|undefined){
 const color=day?.fastingColor.toLowerCase(),label=day?.foodLabel.trim()
 return color&&mealColors.has(color)&&label?{color,label}:undefined
}

export function calendarEvents(events: CalendarEvent[], level: CalendarLevel, otherEvents: { id: string }[] = []): CalendarEvent[] {
  // The compatible BibleDesktop day retains old events and adds a separate rules list.
  const otherIds = new Set(otherEvents.map((event) => event.id))
  const values = events.filter((event) => !event.is_fasting && !otherIds.has(event.id))
  return level === 'major' ? values.filter((event) => event.type_code != null
    ? event.type_code <= 2 || event.type_code === 9 || event.is_icon_commemoration
    : event.type || event.is_icon_commemoration) : values
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
