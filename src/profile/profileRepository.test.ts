import { describe, expect, it } from 'vitest'
import { createConfiguration, type ConfigurationDraft } from './configuration'
import { createLocalProfileRepository, type KeyValueStorage } from './profileRepository'

const draft: ConfigurationDraft = {
  interfaceLanguage: 'ru',
  setupMode: 'manual',
  preset: null,
  sections: ['bible'],
  translationCodes: ['BQ_RUSSIAN_RST_STRONG'],
  morningPrayer: false,
  eveningPrayer: false,
  prayerBook: false,
  akathists: false,
  canons: false,
  horologion: false,
  prayerLanguageCodes: ['ru'],
  calendarLevel: 'major',
  notificationsEnabled: false,
  notificationTime: '08:00',
}

function createMemoryStorage(): KeyValueStorage {
  const values = new Map<string, string>()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  }
}

describe('local profile repository', () => {
  it('persists and removes a configuration', () => {
    const repository = createLocalProfileRepository(createMemoryStorage())
    const configuration = createConfiguration(draft)

    repository.save(configuration)
    expect(repository.load()).toEqual(configuration)

    repository.remove()
    expect(repository.load()).toBeUndefined()
  })

  it('ignores malformed stored data', () => {
    const storage = createMemoryStorage()
    storage.setItem('bible-desktop:profile:v1', '{broken')

    expect(createLocalProfileRepository(storage).load()).toBeUndefined()
  })

  it('migrates an existing version 1 profile without losing choices', () => {
    const storage = createMemoryStorage()
    storage.setItem('bible-desktop:profile:v1', JSON.stringify({
      version: 1, setupMode: 'manual', preset: null, sections: ['bible', 'prayers'],
      bible: { translationCode: 'BQ_RUSSIAN_RST_STRONG' },
      prayers: { morning: true, evening: false, prayerBook: true },
      calendar: { level: 'major' }, notifications: { enabled: false, time: '08:00' },
      createdAt: '2026-09-18T00:00:00.000Z', updatedAt: '2026-09-18T00:00:00.000Z',
    }))

    const migrated = createLocalProfileRepository(storage).load()
    expect(migrated?.version).toBe(2)
    expect(migrated?.interfaceLanguage).toBe('ru')
    expect(migrated?.bible.translationCodes).toEqual(['BQ_RUSSIAN_RST_STRONG'])
    expect(migrated?.prayers.languageCodes).toEqual(['ru'])
  })
})
