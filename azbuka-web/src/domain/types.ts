export type Locale = 'ru' | 'de'

export interface LocalizedText {
  ru: string
  de: string
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

export type ExerciseKind = 'glyph-to-name' | 'name-to-glyph' | 'numeric-value'

export interface ExerciseQuestion {
  id: string
  kind: ExerciseKind
  prompt: string
  options: Array<{ id: string; label: string }>
  correctOptionId: string
  letterId: string
}
