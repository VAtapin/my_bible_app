import { describe, expect, it } from 'vitest'
import { createConfiguration, sectionsForPreset, type ConfigurationDraft } from './configuration'

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
