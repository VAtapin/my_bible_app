import type { CalendarDay, PrayerDetail, PrayerCatalog, LiturgicalWorkSummary, LiturgicalWorkVersion } from '@/api/contracts'

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
export interface StoredPrayerCatalog {key:string;savedAt:string;data:PrayerCatalog}
export interface StoredLiturgicalVersion {key:string;savedAt:string;data:LiturgicalWorkVersion;workRevision?:string|null;collection?:string}

export interface DailyContentRepository {
  getPrayer(id: number,language?:string): Promise<StoredPrayer | undefined>
  putPrayer(value: StoredPrayer): Promise<void>
  listPrayers(): Promise<StoredPrayer[]>
  invalidatePrayer?(id:number,language?:string):Promise<void>
  getCalendarDay(date: string): Promise<StoredCalendarDay | undefined>
  putCalendarDay(value: StoredCalendarDay): Promise<void>
  listCalendarDays(): Promise<StoredCalendarDay[]>
  getPrayerCatalog?():Promise<StoredPrayerCatalog|undefined>
  isReviewedPrayerCatalog?():Promise<boolean>
  putPrayerCatalog?(value:StoredPrayerCatalog):Promise<void>
  getLiturgicalWork?(slug:string):Promise<LiturgicalWorkSummary|undefined>
  putLiturgicalWork?(value:LiturgicalWorkSummary):Promise<void>
  getLiturgicalVersion?(slug:string,language:string,edition?:string):Promise<StoredLiturgicalVersion|undefined>
  putLiturgicalVersion?(value:StoredLiturgicalVersion,requestedSlug:string,requestedEdition?:string):Promise<void>
  invalidateLiturgicalVersion?(slug:string,language:string,edition?:string):Promise<void>
}
