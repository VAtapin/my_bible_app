export const sectionIds = ['bible', 'prayers', 'calendar', 'study'] as const
export type AppSectionId = typeof sectionIds[number]

export const presetIds = ['daily', 'bible', 'prayer', 'calendar'] as const
export type PresetId = typeof presetIds[number]

export type SetupMode = 'quick' | 'manual'
export type CalendarLevel = 'major' | 'all'

export interface AppConfiguration {
  version: 1
  setupMode: SetupMode
  preset: PresetId | null
  sections: AppSectionId[]
  bible: {
    translationCode: string
  }
  prayers: {
    morning: boolean
    evening: boolean
    prayerBook: boolean
  }
  calendar: {
    level: CalendarLevel
  }
  notifications: {
    enabled: boolean
    time: string
  }
  createdAt: string
  updatedAt: string
}

export interface ConfigurationDraft {
  setupMode: SetupMode
  preset: PresetId | null
  sections: AppSectionId[]
  translationCode: string
  morningPrayer: boolean
  eveningPrayer: boolean
  prayerBook: boolean
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
  return {
    version: 1,
    setupMode: draft.setupMode,
    preset: draft.setupMode === 'quick' ? draft.preset : null,
    sections,
    bible: { translationCode: draft.translationCode.trim() || 'BQ_RUSSIAN_RST_STRONG' },
    prayers: {
      morning: draft.morningPrayer,
      evening: draft.eveningPrayer,
      prayerBook: draft.prayerBook,
    },
    calendar: { level: draft.calendarLevel },
    notifications: {
      enabled: draft.notificationsEnabled,
      time: isTime(draft.notificationTime) ? draft.notificationTime : '08:00',
    },
    createdAt: previous?.createdAt ?? timestamp,
    updatedAt: timestamp,
  }
}

export function isAppConfiguration(value: unknown): value is AppConfiguration {
  if (!isRecord(value) || value.version !== 1) {
    return false
  }

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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isTime(value: string): boolean {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(value)
}
