import { isInterfaceLanguage, type InterfaceLanguage } from '@/i18n/locale'

export const sectionIds = ['bible', 'prayers', 'calendar', 'study'] as const
export type AppSectionId = typeof sectionIds[number]

export const presetIds = ['daily', 'bible', 'prayer', 'calendar'] as const
export type PresetId = typeof presetIds[number]

export type SetupMode = 'quick' | 'manual'
export type CalendarLevel = 'major' | 'all'
export const prayerContentIds = ['morning', 'evening', 'prayerBook', 'akathists', 'canons', 'horologion'] as const
export type PrayerContentId = typeof prayerContentIds[number]

export interface AppConfiguration {
  version: 2
  interfaceLanguage: InterfaceLanguage
  setupMode: SetupMode
  preset: PresetId | null
  sections: AppSectionId[]
  bible: {
    translationCode: string
    translationCodes: string[]
  }
  prayers: {
    morning: boolean
    evening: boolean
    prayerBook: boolean
    akathists: boolean
    canons: boolean
    horologion: boolean
    languageCodes: string[]
  }
  calendar: {
    level: CalendarLevel
    languageCode: string
  }
  notifications: {
    enabled: boolean
    time: string
  }
  createdAt: string
  updatedAt: string
}

export interface ConfigurationDraft {
  interfaceLanguage: InterfaceLanguage
  setupMode: SetupMode
  preset: PresetId | null
  sections: AppSectionId[]
  translationCodes: string[]
  morningPrayer: boolean
  eveningPrayer: boolean
  prayerBook: boolean
  akathists: boolean
  canons: boolean
  horologion: boolean
  prayerLanguageCodes: string[]
  calendarLevel: CalendarLevel
  notificationsEnabled: boolean
  notificationTime: string
}

const presetSections: Record<PresetId, AppSectionId[]> = {
  daily: ['bible', 'prayers', 'calendar'],
  bible: ['bible', 'study'],
  prayer: ['prayers'],
  calendar: ['calendar'],
}

export function sectionsForPreset(preset: PresetId): AppSectionId[] {
  return [...presetSections[preset]]
}

export function createConfiguration(
  draft: ConfigurationDraft,
  previous?: AppConfiguration,
  now = new Date(),
): AppConfiguration {
  const sections = sectionIds.filter((section) => draft.sections.includes(section))
  if (sections.length === 0) {
    throw new Error('sections-required')
  }

  const timestamp = now.toISOString()
  const translationCodes = uniqueNonEmpty(draft.translationCodes)
  const prayerLanguageCodes = uniqueNonEmpty(draft.prayerLanguageCodes)
  return {
    version: 2,
    interfaceLanguage: draft.interfaceLanguage,
    setupMode: draft.setupMode,
    preset: draft.setupMode === 'quick' ? draft.preset : null,
    sections,
    bible: {
      translationCode: translationCodes[0] ?? defaultTranslationCode(draft.interfaceLanguage),
      translationCodes: translationCodes.length ? translationCodes : [defaultTranslationCode(draft.interfaceLanguage)],
    },
    prayers: {
      morning: draft.morningPrayer,
      evening: draft.eveningPrayer,
      prayerBook: draft.prayerBook,
      akathists: draft.akathists,
      canons: draft.canons,
      horologion: draft.horologion,
      languageCodes: prayerLanguageCodes.length ? prayerLanguageCodes : [draft.interfaceLanguage],
    },
    calendar: { level: draft.calendarLevel, languageCode: draft.interfaceLanguage },
    notifications: {
      enabled: draft.notificationsEnabled,
      time: isTime(draft.notificationTime) ? draft.notificationTime : '08:00',
    },
    createdAt: previous?.createdAt ?? timestamp,
    updatedAt: timestamp,
  }
}

export function isAppConfiguration(value: unknown): value is AppConfiguration {
  if (!isRecord(value) || value.version !== 2) {
    return false
  }

  return isInterfaceLanguage(value.interfaceLanguage)
    && (value.setupMode === 'quick' || value.setupMode === 'manual')
    && (value.preset === null || presetIds.includes(value.preset as PresetId))
    && Array.isArray(value.sections)
    && value.sections.length > 0
    && value.sections.every((section) => sectionIds.includes(section as AppSectionId))
    && isRecord(value.bible)
    && typeof value.bible.translationCode === 'string'
    && isStringArray(value.bible.translationCodes)
    && value.bible.translationCodes.length > 0
    && isRecord(value.prayers)
    && typeof value.prayers.morning === 'boolean'
    && typeof value.prayers.evening === 'boolean'
    && typeof value.prayers.prayerBook === 'boolean'
    && typeof value.prayers.akathists === 'boolean'
    && typeof value.prayers.canons === 'boolean'
    && typeof value.prayers.horologion === 'boolean'
    && isStringArray(value.prayers.languageCodes)
    && value.prayers.languageCodes.length > 0
    && isRecord(value.calendar)
    && (value.calendar.level === 'major' || value.calendar.level === 'all')
    && typeof value.calendar.languageCode === 'string'
    && isRecord(value.notifications)
    && typeof value.notifications.enabled === 'boolean'
    && typeof value.notifications.time === 'string'
    && isTime(value.notifications.time)
    && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string'
}

export function migrateAppConfiguration(value: unknown): AppConfiguration | undefined {
  if (isAppConfiguration(value)) return value
  if (!isLegacyConfiguration(value)) return undefined

  return {
    ...value,
    version: 2,
    interfaceLanguage: 'ru',
    bible: {
      translationCode: value.bible.translationCode,
      translationCodes: [value.bible.translationCode],
    },
    prayers: {
      ...value.prayers,
      akathists: false,
      canons: false,
      horologion: false,
      languageCodes: ['ru'],
    },
    calendar: { ...value.calendar, languageCode: 'ru' },
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string' && item.trim() !== '')
}

function uniqueNonEmpty(values: string[]): string[] {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))]
}

function defaultTranslationCode(language: InterfaceLanguage): string {
  return language === 'de' ? 'BQ_GERMAN_ELBERFELD_STRONG' : 'BQ_RUSSIAN_RST_STRONG'
}

function isLegacyConfiguration(value: unknown): value is Omit<AppConfiguration, 'version' | 'interfaceLanguage' | 'bible' | 'prayers' | 'calendar'> & {
  version: 1
  bible: { translationCode: string }
  prayers: { morning: boolean; evening: boolean; prayerBook: boolean }
  calendar: { level: CalendarLevel }
} {
  if (!isRecord(value) || value.version !== 1) return false
  return (value.setupMode === 'quick' || value.setupMode === 'manual')
    && (value.preset === null || presetIds.includes(value.preset as PresetId))
    && Array.isArray(value.sections)
    && value.sections.length > 0
    && value.sections.every((section) => sectionIds.includes(section as AppSectionId))
    && isRecord(value.bible)
    && typeof value.bible.translationCode === 'string'
    && isRecord(value.prayers)
    && typeof value.prayers.morning === 'boolean'
    && typeof value.prayers.evening === 'boolean'
    && typeof value.prayers.prayerBook === 'boolean'
    && isRecord(value.calendar)
    && (value.calendar.level === 'major' || value.calendar.level === 'all')
    && isRecord(value.notifications)
    && typeof value.notifications.enabled === 'boolean'
    && typeof value.notifications.time === 'string'
    && isTime(value.notifications.time)
    && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string'
}

function isTime(value: string): boolean {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(value)
}
