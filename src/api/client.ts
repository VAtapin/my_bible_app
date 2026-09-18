import type {
  ApiEnvelope,
  BibleBook,
  BibleChapter,
  CalendarDay,
  PrayerDetail,
  PrayerSummary,
  TranslationSummary,
} from './contracts'

export type ApiErrorKind = 'offline' | 'timeout' | 'http' | 'invalid-response'

export class ApiError extends Error {
  constructor(
    public readonly kind: ApiErrorKind,
    message: string,
    public readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface BibleApi {
  getTranslations(language?: string): Promise<TranslationSummary[]>
  getBooks(translationCode: string): Promise<BibleBook[]>
  getChapter(translationCode: string, bookSlug: string, chapter: number): Promise<BibleChapter>
  getPrayers(language?: string): Promise<PrayerSummary[]>
  getPrayer(id: number): Promise<PrayerDetail>
  getCalendarDay(date: string, language?: string, profile?: 'typikon-strict' | 'parish'): Promise<CalendarDay>
}

interface ApiClientOptions {
  baseUrl: string
  timeoutMs?: number
  fetcher?: typeof fetch
}

export function createBibleApi({ baseUrl, timeoutMs = 10_000, fetcher = fetch }: ApiClientOptions): BibleApi {
  const request = async <T>(path: string, validate: (data: unknown) => data is T): Promise<T> => {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), timeoutMs)

    try {
      const response = await fetcher(`${baseUrl.replace(/\/$/, '')}${path}`, {
        headers: { Accept: 'application/json' },
        signal: controller.signal,
      })

      if (!response.ok) {
        throw new ApiError('http', `API вернуло статус ${response.status}.`, response.status)
      }

      const payload: unknown = await response.json()
      if (!isEnvelope(payload) || !validate(payload.data)) {
        throw new ApiError('invalid-response', 'API вернуло ответ неизвестного формата.')
      }

      return payload.data
    } catch (error) {
      if (error instanceof ApiError) {
        throw error
      }
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new ApiError('timeout', 'API не ответило вовремя.')
      }
      throw new ApiError('offline', 'Нет соединения с API.')
    } finally {
      clearTimeout(timeout)
    }
  }

  return {
    getTranslations(language) {
      const query = language ? `?language=${encodeURIComponent(language)}` : ''
      return request<TranslationSummary[]>(`/translations${query}`, isTranslationList)
    },
    async getBooks(translationCode) {
      const data = await request<{ books: BibleBook[] }>(
        `/translations/${encodeURIComponent(translationCode)}/books`,
        isBookListPayload,
      )
      return data.books
    },
    getChapter(translationCode, bookSlug, chapter) {
      return request<BibleChapter>(
        `/translations/${encodeURIComponent(translationCode)}/books/${encodeURIComponent(bookSlug)}/chapters/${chapter}`,
        isBibleChapter,
      )
    },
    getPrayers(language = 'ru') {
      return request<PrayerSummary[]>(`/prayers?language=${encodeURIComponent(language)}`, isPrayerList)
    },
    getPrayer(id) {
      return request<PrayerDetail>(`/prayers/${id}`, isPrayerDetail)
    },
    getCalendarDay(date, language = 'ru', profile = 'typikon-strict') {
      const query = new URLSearchParams({ date, lang: language, profile })
      return request<CalendarDay>(`/calendar/day?${query}`, isCalendarDay)
    },
  }
}

function isEnvelope(payload: unknown): payload is ApiEnvelope<unknown> {
  return typeof payload === 'object' && payload !== null && 'data' in payload
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isTranslationList(value: unknown): value is TranslationSummary[] {
  return Array.isArray(value) && value.every(isTranslationSummary)
}

function isBookListPayload(value: unknown): value is { books: BibleBook[] } {
  return isRecord(value) && Array.isArray(value.books) && value.books.every(isBibleBook)
}

function isBibleChapter(value: unknown): value is BibleChapter {
  if (!isRecord(value) || !isRecord(value.translation) || !isRecord(value.book) || !isRecord(value.chapter)) {
    return false
  }

  return typeof value.translation.code === 'string'
    && typeof value.translation.name === 'string'
    && isNullableString(value.translation.short_name)
    && isLanguageSummary(value.translation.language)
    && typeof value.book.slug === 'string'
    && typeof value.book.name === 'string'
    && isNullableString(value.book.short_name)
    && typeof value.book.chapters_count === 'number'
    && typeof value.chapter.number === 'number'
    && typeof value.chapter.verses_count === 'number'
    && Array.isArray(value.verses)
    && value.verses.every(isBibleVerse)
}

function isTranslationSummary(value: unknown): value is TranslationSummary {
  return isRecord(value)
    && typeof value.code === 'string'
    && typeof value.name === 'string'
    && isNullableString(value.short_name)
    && isLanguageSummary(value.language)
    && (typeof value.canon_code === 'string' || value.canon_code === null)
    && typeof value.has_old_testament === 'boolean'
    && typeof value.has_new_testament === 'boolean'
    && typeof value.has_apocrypha === 'boolean'
    && typeof value.has_strong === 'boolean'
    && typeof value.is_default === 'boolean'
}

function isBibleBook(value: unknown): value is BibleBook {
  return isRecord(value)
    && typeof value.slug === 'string'
    && typeof value.name === 'string'
    && isNullableString(value.short_name)
    && typeof value.order === 'number'
    && typeof value.chapters_count === 'number'
}

function isBibleVerse(value: unknown): value is BibleChapter['verses'][number] {
  return isRecord(value)
    && typeof value.id === 'number'
    && typeof value.number === 'number'
    && typeof value.osis_ref === 'string'
    && typeof value.text === 'string'
    && typeof value.plain_text === 'string'
    && typeof value.has_strong_markup === 'boolean'
}

function isLanguageSummary(value: unknown): value is TranslationSummary['language'] {
  return isRecord(value) && typeof value.code === 'string' && typeof value.name === 'string'
}

function isNullableString(value: unknown): value is string | null {
  return typeof value === 'string' || value === null
}

function isPrayerList(value: unknown): value is PrayerSummary[] {
  return Array.isArray(value) && value.every(isPrayerSummary)
}

function isPrayerSummary(value: unknown): value is PrayerSummary {
  return isRecord(value)
    && typeof value.id === 'number'
    && typeof value.language_code === 'string'
    && typeof value.category === 'string'
    && isNullableString(value.liturgy_key)
    && typeof value.title === 'string'
    && isNullableString(value.short_title)
    && isNullableString(value.intro)
    && typeof value.excerpt === 'string'
}

function isPrayerDetail(value: unknown): value is PrayerDetail {
  return isRecord(value)
    && typeof value.id === 'number'
    && typeof value.language_code === 'string'
    && typeof value.category === 'string'
    && isNullableString(value.liturgy_key)
    && typeof value.title === 'string'
    && isNullableString(value.short_title)
    && isNullableString(value.intro)
    && typeof value.body === 'string'
    && isNullableString(value.source_url)
    && Array.isArray(value.sections)
}

function isCalendarDay(value: unknown): value is CalendarDay {
  return isRecord(value)
    && typeof value.date === 'string'
    && typeof value.old_style_date === 'string'
    && typeof value.pascha_date === 'string'
    && typeof value.liturgical_period === 'string'
    && typeof value.source === 'string'
    && isRecord(value.metadata)
    && Array.isArray(value.events)
    && value.events.every(isCalendarEvent)
    && Array.isArray(value.fasting_events)
    && value.fasting_events.every(isCalendarEvent)
    && Array.isArray(value.readings)
    && value.readings.every(isCalendarReading)
}

function isCalendarEvent(value: unknown): value is CalendarDay['events'][number] {
  return isRecord(value)
    && typeof value.id === 'string'
    && typeof value.name === 'string'
    && typeof value.is_fasting === 'boolean'
}

function isCalendarReading(value: unknown): value is CalendarDay['readings'][number] {
  return isRecord(value)
    && typeof value.id === 'string'
    && typeof value.title === 'string'
    && typeof value.display_ref === 'string'
    && typeof value.passage_ref === 'string'
}
