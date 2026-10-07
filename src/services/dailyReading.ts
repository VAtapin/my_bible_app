import type { CalendarReading } from '@/api/contracts'
import type { ChapterService } from './chapterService'
import { calendarReadingLink } from './calendarPresentation'

export async function loadGospelExcerpt(service: ChapterService, readings: CalendarReading[], translation: string) {
  const reading = readings.find((item) => ['Matt', 'Mark', 'Luke', 'John'].includes(item.reading?.passages[0]?.book ?? ''))
  const route = reading && calendarReadingLink(reading)
  const passage = reading?.reading?.passages[0]
  if (!reading || !route || !passage) return undefined
  let chapter = await service.readOffline(translation, route.query.book, passage.start.chapter)
  if (!chapter) {
    try { chapter = await service.download(translation, route.query.book, passage.start.chapter) }
    catch { return undefined }
  }
  const verse = chapter.verses.find((item) => item.number === (passage.start.verse ?? 1))
  return verse ? { text: verse.plain_text, reference: reading.display_ref, route: { ...route, query: { ...route.query, translation } } } : undefined
}
