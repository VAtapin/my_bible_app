export interface LanguageSummary {
  code: string
  name: string
}

export interface TranslationSummary {
  code: string
  name: string
  short_name: string | null
  language: LanguageSummary
  canon_code: string | null
  has_old_testament: boolean
  has_new_testament: boolean
  has_apocrypha: boolean
  has_strong: boolean
  is_default: boolean
}

export interface BibleBook {
  slug: string
  name: string
  short_name: string | null
  order: number
  chapters_count: number
}

export interface BibleVerse {
  id: number
  number: number
  osis_ref: string
  text: string
  plain_text: string
  has_strong_markup: boolean
}

export interface BibleChapter {
  translation: {
    code: string
    name: string
    short_name: string | null
    language: LanguageSummary
  }
  book: {
    slug: string
    name: string
    short_name: string | null
    chapters_count: number
  }
  chapter: {
    number: number
    verses_count: number
  }
  verses: BibleVerse[]
}

export interface ApiEnvelope<T> {
  data: T
}

export interface PrayerSummary {
  id: number
  language_code: string
  category: string
  liturgy_key: string | null
  title: string
  short_title: string | null
  intro: string | null
  excerpt: string
}

export interface PrayerDetail extends Omit<PrayerSummary, 'excerpt'> {
  body: string
  source_url: string | null
  sections: Array<{ id: number; title: string | null; sort_order: number }>
}

export interface CalendarReadingPassage {
  book: string
  start: { chapter: number; verse: number | null }
  end: { chapter: number; verse: number | null }
}

export interface CalendarReading {
  id: string
  type: string
  title: string
  display_ref: string
  passage_ref: string
  date_rule_type: string
  reading?: {
    schemaVersion: number
    parseStatus: string
    passages: CalendarReadingPassage[]
  }
}

export interface CalendarEvent {
  id: string
  name: string
  is_icon_commemoration: boolean
  is_fasting: boolean
  typicon_icon?: string | null
  type?: { code: string; name: string } | null
  metadata?: Record<string, unknown> | unknown[]
}

export interface CalendarDay {
  date: string
  old_style_date: string
  pascha_date: string
  liturgical_period: string
  source: string
  metadata: { apiVersion: string; language: string; fastingProfileId: string }
  events: CalendarEvent[]
  fasting_events: CalendarEvent[]
  readings: CalendarReading[]
}
