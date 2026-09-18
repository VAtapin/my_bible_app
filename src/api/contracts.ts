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
