import { describe, expect, it } from 'vitest'
import { createConfiguration, type ConfigurationDraft } from './configuration'
import { createLocalProfileRepository, type KeyValueStorage } from './profileRepository'

const draft: ConfigurationDraft = {
  setupMode: 'manual',
  preset: null,
  sections: ['bible'],
  translationCode: 'BQ_RUSSIAN_RST_STRONG',
  morningPrayer: false,
  eveningPrayer: false,
  prayerBook: false,
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
})
