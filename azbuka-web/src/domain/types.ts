export type Locale = 'cu' | 'ru' | 'de'

export interface LocalizedText {
  ru: string
  de: string
  cu?: string
}

export const localizedText = (text: LocalizedText, locale: Locale): string => {
  if (locale === 'de') return text.de
  if (locale === 'cu') return text.cu ?? text.ru
  return text.ru
}

export interface Letter {
  id: string
  glyph: string
  name: LocalizedText
  transliteration: string
  meaning: LocalizedText
  numericValue: number | null
  group: 'basic' | 'historic' | 'numeral'
}

export interface UserProfile {
  locale: Locale
  dailyGoal: number
  learnedLetterIds: string[]
  answered: number
  correct: number
  todayAnswered: number
  todayDate: string | null
  streak: number
  lastPracticeDate: string | null
}

export type ExerciseKind = 'glyph-to-name' | 'name-to-glyph'

export interface ExerciseQuestion {
  id: string
  kind: ExerciseKind
  prompt: string
  options: Array<{ id: string; label: string }>
  correctOptionId: string
  letterId: string
}
