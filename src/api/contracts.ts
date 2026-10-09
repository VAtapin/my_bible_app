export interface LanguageSummary {
  code: string
  name: string
  native_name?: string
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
  canonical_book?: { osis_code: string; testament?: string } | null
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

export interface LiturgicalEditionSummary {
  code: string
  title: string
  language: string
  orthography: string
  reader_profile: string
}

export interface LiturgicalWorkSummary {
  id: number
  slug: string
  title: string
  collections: string[]
  available_languages: string[]
  editions: LiturgicalEditionSummary[]
  source_url: string | null
}

export interface LiturgicalBlock {
  id: string
  kind: string
  text: string
}

export interface LiturgicalWorkVersion {
  slug: string
  title: string
  language: string
  edition: string
  edition_title: string
  orthography: string
  reader_profile: string
  blocks: LiturgicalBlock[]
  credit: string
  source_url: string
  content_hash: string
  review_status: string
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
  type_code?: number | null
  typikon_mark?: { label: string; image_url: string } | null
  description?: string | null
  type?: { code: string; name: string } | null
  metadata?: Record<string, unknown> | unknown[]
}

export interface CalendarDay {
  other_events?: { id: string; name: string; category: string; description?: string | null }[]
  day_style?: { rank: string; color: string; fontWeight: number } | null
  food?: { label: string; reason: string | null; color: string | null; image_url?: string | null }
  memorial_markers?: { label: string; image_url: string }[]
  tone?: number | null
  week_after_pentecost?: number | null
  icons?: CalendarIcon[]
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

export interface CalendarServicePlan {
  date: string
  textLanguage: string
  assignments: { textId: string | number; title: string; slot: string; text: string; insert?: boolean; rubric?: string | null }[]
  expansions: { id: string; title: string; text: string }[]
  properCoverage?: { message: string }
}

export interface CalendarIcon {
  id: number
  title: string
  image_url: string | null
  imagePreviewUrl?: string | null
  credit?: string | null
  description?: string | null
  calendar_record_ids?: string[]
  images?: { url: string }[]
  dates?: { label: string }[]
}
export interface CalendarIconDetail { id: number; calendarRecordIds: string[] }

export interface VerseSearchResult {
  verse_id: number
  reference: string
  translation: { code: string }
  book: { slug: string; osis_code?: string }
  chapter_number: number
  verse_number: number
  snippet: string
  text?: string
  snippet_segments?: { text: string; match: boolean }[]
}
export interface VerseSearchResponse { results: VerseSearchResult[] }
