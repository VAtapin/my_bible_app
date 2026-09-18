import { migrateAppConfiguration, type AppConfiguration } from './configuration'

const storageKey = 'bible-desktop:profile:v1'

export interface ProfileRepository {
  load(): AppConfiguration | undefined
  save(configuration: AppConfiguration): void
  remove(): void
}

export interface KeyValueStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

export function createLocalProfileRepository(storage: KeyValueStorage): ProfileRepository {
  return {
    load() {
      const serialized = storage.getItem(storageKey)
      if (!serialized) {
        return undefined
      }

      try {
      const value: unknown = JSON.parse(serialized)
      return migrateAppConfiguration(value)
      } catch {
        return undefined
      }
    },
    save(configuration) {
      storage.setItem(storageKey, JSON.stringify(configuration))
    },
    remove() {
      storage.removeItem(storageKey)
    },
  }
}
