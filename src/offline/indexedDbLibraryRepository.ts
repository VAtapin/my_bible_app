import { offlineStores, openOfflineDatabase, runRequest } from './database'
import type { Bookmark, LibraryRepository, OfflinePackage, ReadingLocation } from './libraryRepository'

const readingLocationKey = 'reading-location'
const packagePrefix = 'package:'

type StateRecord =
  | { key: typeof readingLocationKey; value: ReadingLocation }
  | { key: string; value: OfflinePackage }

export function createIndexedDbLibraryRepository(): LibraryRepository {
  return {
    async getReadingLocation() {
      const record = await getState(readingLocationKey)
      return record?.value as ReadingLocation | undefined
    },
    saveReadingLocation(location) {
      return putState({ key: readingLocationKey, value: location })
    },
    async listBookmarks() {
      return withDatabase(async (database) => runRequest<Bookmark[]>(
        database.transaction(offlineStores.bookmarks, 'readonly').objectStore(offlineStores.bookmarks).getAll(),
      ))
    },
    async putBookmark(bookmark) {
      await withDatabase(async (database) => runRequest(
        database.transaction(offlineStores.bookmarks, 'readwrite').objectStore(offlineStores.bookmarks).put(bookmark),
      ))
    },
    async deleteBookmark(key) {
      await withDatabase(async (database) => runRequest(
        database.transaction(offlineStores.bookmarks, 'readwrite').objectStore(offlineStores.bookmarks).delete(key),
      ))
    },
    async getPackage(translationCode) {
      const record = await getState(`${packagePrefix}${translationCode}`)
      return record?.value as OfflinePackage | undefined
    },
    async listPackages() {
      const records = await withDatabase(async (database) => runRequest<StateRecord[]>(
        database.transaction(offlineStores.state, 'readonly').objectStore(offlineStores.state).getAll(),
      ))
      return records
        .filter((record) => record.key.startsWith(packagePrefix))
        .map((record) => record.value as OfflinePackage)
    },
    putPackage(value) {
      return putState({ key: `${packagePrefix}${value.translationCode}`, value })
    },
    async deletePackage(translationCode) {
      await withDatabase(async (database) => runRequest(
        database.transaction(offlineStores.state, 'readwrite').objectStore(offlineStores.state)
          .delete(`${packagePrefix}${translationCode}`),
      ))
    },
  }
}

async function getState(key: string): Promise<StateRecord | undefined> {
  return withDatabase(async (database) => runRequest<StateRecord | undefined>(
    database.transaction(offlineStores.state, 'readonly').objectStore(offlineStores.state).get(key),
  ))
}

async function putState(record: StateRecord): Promise<void> {
  await withDatabase(async (database) => runRequest(
    database.transaction(offlineStores.state, 'readwrite').objectStore(offlineStores.state).put(record),
  ))
}

async function withDatabase<T>(action: (database: IDBDatabase) => Promise<T>): Promise<T> {
  const database = await openOfflineDatabase()
  try {
    return await action(database)
  } finally {
    database.close()
  }
}
