import { isInterfaceLanguage, defaultBibleTranslations, type InterfaceLanguage } from '@/i18n/locale'

export const sectionIds = ['bible', 'prayers', 'calendar', 'study'] as const
export type AppSectionId = typeof sectionIds[number]

export const presetIds = ['daily', 'bible', 'prayer', 'calendar', 'education'] as const
export type PresetId = typeof presetIds[number]

export type SetupMode = 'quick' | 'manual'
export type CalendarLevel = 'major' | 'all'
export type EducationPluginId = 'azbuka'
export interface EducationSettings {
  pluginIds: EducationPluginId[]
  showClock: boolean
  showProgress: boolean
}
export interface CalendarHomeSettings {
  oldStyle: boolean
  fasting: boolean
  commemorations: boolean
  readings: boolean
  compact: boolean
}
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
    home?: CalendarHomeSettings
  }
  education?: EducationSettings
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
  calendarHome?: CalendarHomeSettings
  educationPluginIds?: EducationPluginId[]
  showEducationClock?: boolean
  showEducationProgress?: boolean
  notificationsEnabled: boolean
  notificationTime: string
}

const presetSections: Record<PresetId, AppSectionId[]> = {
  daily: ['bible', 'prayers', 'calendar'],
  bible: ['bible'],
  prayer: ['prayers'],
  calendar: ['calendar'],
  education: ['study'],
}

export function sectionsForPreset(preset: PresetId): AppSectionId[] {
  return [...presetSections[preset]]
}

/** One-click start enables every module, without downloads or permission prompts. */
export function createCompleteConfiguration(language: InterfaceLanguage, now = new Date()): AppConfiguration {
  return createConfiguration({
    interfaceLanguage: language, setupMode: 'quick', preset: null,
    sections: [...sectionIds], translationCodes: [defaultBibleTranslations[language]],
    morningPrayer: true, eveningPrayer: true, prayerBook: true,
    akathists: true, canons: true, horologion: true,
    prayerLanguageCodes: [...new Set([language, 'ru', 'cu', 'cu-civil'])],
    calendarLevel: 'all', calendarHome: defaultCalendarHome(),
    educationPluginIds: ['azbuka'], showEducationClock: true, showEducationProgress: true,
    notificationsEnabled: false, notificationTime: '08:00',
  }, undefined, now)
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
  const pluginIds = sections.includes('study')
    ? [...new Set((draft.educationPluginIds ?? ['azbuka']).filter((id) => id === 'azbuka'))]
    : []
  if (sections.includes('study') && !pluginIds.length) throw new Error('education-required')
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
    calendar: { level: draft.calendarLevel, languageCode: draft.interfaceLanguage, home: draft.calendarHome ?? defaultCalendarHome() },
    education: {
      pluginIds,
      showClock: pluginIds.includes('azbuka'),
      showProgress: draft.showEducationProgress ?? true,
    },
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
    && (value.calendar.home === undefined || isCalendarHome(value.calendar.home))
    && (value.education === undefined || isEducationSettings(value.education))
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

export function defaultCalendarHome(): CalendarHomeSettings {
  return { oldStyle: true, fasting: true, commemorations: true, readings: true, compact: true }
}

export function educationSettings(configuration?: AppConfiguration): EducationSettings {
  if (!configuration?.sections.includes('study')) return { pluginIds: [], showClock: false, showProgress: false }
  const settings = configuration.education ?? { pluginIds: ['azbuka'], showClock: true, showProgress: true }
  return { ...settings, showClock: settings.pluginIds.includes('azbuka') }
}

function isCalendarHome(value: unknown): value is CalendarHomeSettings {
  return isRecord(value) && ['oldStyle', 'fasting', 'commemorations', 'readings', 'compact'].every((key) => typeof value[key] === 'boolean')
}

function isEducationSettings(value: unknown): value is EducationSettings {
  return isRecord(value) && Array.isArray(value.pluginIds)
    && value.pluginIds.every((id) => id === 'azbuka')
    && typeof value.showClock === 'boolean' && typeof value.showProgress === 'boolean'
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
  return defaultBibleTranslations[language]
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
