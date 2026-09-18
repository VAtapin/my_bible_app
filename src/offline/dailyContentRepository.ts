import type { CalendarDay, PrayerDetail } from '@/api/contracts'

export interface StoredPrayer {
  key: string
  savedAt: string
  data: PrayerDetail
}

export interface StoredCalendarDay {
  key: string
  savedAt: string
  data: CalendarDay
}

export interface DailyContentRepository {
  getPrayer(id: number): Promise<StoredPrayer | undefined>
  putPrayer(value: StoredPrayer): Promise<void>
  listPrayers(): Promise<StoredPrayer[]>
  getCalendarDay(date: string): Promise<StoredCalendarDay | undefined>
  putCalendarDay(value: StoredCalendarDay): Promise<void>
  listCalendarDays(): Promise<StoredCalendarDay[]>
}
