import { describe, expect, it } from 'vitest'
import { createConfiguration, sectionsForPreset, educationSettings, isAppConfiguration, migrateAppConfiguration, defaultCalendarHome, type ConfigurationDraft } from './configuration'

const draft: ConfigurationDraft = {
  interfaceLanguage: 'ru',
  setupMode: 'quick',
  preset: 'daily',
  sections: ['calendar', 'bible', 'calendar', 'prayers'],
  translationCodes: [' BQ_RUSSIAN_RST_STRONG ', 'BQ_GERMAN_ELBERFELD_STRONG'],
  morningPrayer: true,
  eveningPrayer: false,
  prayerBook: true,
  akathists: true,
  canons: false,
  horologion: false,
  prayerLanguageCodes: ['ru', 'de'],
  calendarLevel: 'major',
  notificationsEnabled: true,
  notificationTime: '07:30',
}

describe('app configuration', () => {
  it.each([['uk', 'BQ_UKRAINE'], ['en', 'BQ_ENGLISH_KJV_1769']] as const)('keeps %s profiles and chooses a verified same-language Bible by default', (interfaceLanguage, translationCode) => {
    const configuration = createConfiguration({ ...draft, interfaceLanguage, translationCodes: [], prayerLanguageCodes: [interfaceLanguage] })
    expect(configuration.bible.translationCode).toBe(translationCode)
    expect(migrateAppConfiguration(JSON.parse(JSON.stringify(configuration)))?.interfaceLanguage).toBe(interfaceLanguage)
    expect(configuration.prayers.languageCodes).toEqual([interfaceLanguage])
  })
  it('always enables the clock when Azbuka is selected, including legacy false settings', () => {
    const configuration = createConfiguration({ ...draft, sections: ['study'], educationPluginIds: ['azbuka'], showEducationClock: false })
    expect(configuration.education?.showClock).toBe(true)
    configuration.education!.showClock = false
    expect(educationSettings(migrateAppConfiguration(JSON.parse(JSON.stringify(configuration)))).showClock).toBe(true)
  })
  it('preserves explicit plugin and dashboard preferences across serialization', () => {
    const configuration = createConfiguration({ ...draft, sections: ['study', 'calendar'], educationPluginIds: ['azbuka', 'azbuka'], showEducationClock: true, showEducationProgress: false, calendarHome: { ...defaultCalendarHome(), oldStyle: false, compact: false } })
    const restored = migrateAppConfiguration(JSON.parse(JSON.stringify(configuration)))!
    expect(educationSettings(restored)).toEqual({ pluginIds: ['azbuka'], showClock: true, showProgress: false })
    expect(restored.calendar.home).toEqual({ oldStyle: false, compact: false, fasting: true, readings: true, commemorations: true })
  })

  it('keeps old education profiles compatible without overwriting them', () => {
    const configuration = createConfiguration({ ...draft, sections: ['study'] })
    delete configuration.education
    delete configuration.calendar.home
    expect(migrateAppConfiguration(configuration)).toBe(configuration)
    expect(educationSettings(configuration).pluginIds).toEqual(['azbuka'])
    expect(configuration.education).toBeUndefined()
  })

  it('does not show disabled plugins or accept malformed display settings', () => {
    const configuration = createConfiguration({ ...draft, educationPluginIds: ['azbuka'] })
    expect(educationSettings(configuration)).toEqual({ pluginIds: [], showClock: false, showProgress: false })
    expect(isAppConfiguration({ ...configuration, education: { pluginIds: ['unknown'], showClock: true, showProgress: true } })).toBe(false)
    expect(isAppConfiguration({ ...configuration, calendar: { ...configuration.calendar, home: { ...defaultCalendarHome(), fasting: 'yes' } } })).toBe(false)
    expect(() => createConfiguration({ ...draft, sections: ['study'], educationPluginIds: [] })).toThrow('education-required')
  })

  it('creates a normalized versioned configuration', () => {
    const configuration = createConfiguration(draft, undefined, new Date('2026-09-18T10:00:00.000Z'))

    expect(configuration.sections).toEqual(['bible', 'prayers', 'calendar'])
    expect(configuration.bible.translationCode).toBe('BQ_RUSSIAN_RST_STRONG')
    expect(configuration.bible.translationCodes).toEqual(['BQ_RUSSIAN_RST_STRONG', 'BQ_GERMAN_ELBERFELD_STRONG'])
    expect(configuration.interfaceLanguage).toBe('ru')
    expect(configuration.createdAt).toBe('2026-09-18T10:00:00.000Z')
  })

  it('keeps creation time while editing', () => {
    const existing = createConfiguration(draft, undefined, new Date('2026-09-18T10:00:00.000Z'))
    const updated = createConfiguration(
      { ...draft, sections: ['bible'] },
      existing,
      new Date('2026-09-18T11:00:00.000Z'),
    )

    expect(updated.createdAt).toBe(existing.createdAt)
    expect(updated.updatedAt).toBe('2026-09-18T11:00:00.000Z')
  })

  it('requires at least one section', () => {
    expect(() => createConfiguration({ ...draft, sections: [] })).toThrow('sections-required')
  })

  it('returns independent preset selections', () => {
    const first = sectionsForPreset('daily')
    first.pop()

    expect(sectionsForPreset('daily')).toEqual(['bible', 'prayers', 'calendar'])
  })
})
