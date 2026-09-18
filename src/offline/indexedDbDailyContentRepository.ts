import type { DailyContentRepository, StoredCalendarDay, StoredPrayer } from './dailyContentRepository'
import { offlineStores, openOfflineDatabase, runRequest } from './database'

export function createIndexedDbDailyContentRepository(): DailyContentRepository {
  return {
    getPrayer(id) {
      return read<StoredPrayer>(offlineStores.prayers, String(id))
    },
    putPrayer(value) {
      return put(offlineStores.prayers, value)
    },
    listPrayers() {
      return list<StoredPrayer>(offlineStores.prayers)
    },
    getCalendarDay(date) {
      return read<StoredCalendarDay>(offlineStores.calendar, date)
    },
    putCalendarDay(value) {
      return put(offlineStores.calendar, value)
    },
    listCalendarDays() {
      return list<StoredCalendarDay>(offlineStores.calendar)
    },
  }
}

async function read<T>(storeName: string, key: string): Promise<T | undefined> {
  return withDatabase((database) => runRequest<T | undefined>(
    database.transaction(storeName, 'readonly').objectStore(storeName).get(key),
  ))
}

async function put(storeName: string, value: unknown): Promise<void> {
  await withDatabase((database) => runRequest(
    database.transaction(storeName, 'readwrite').objectStore(storeName).put(value),
  ))
}

async function list<T>(storeName: string): Promise<T[]> {
  return withDatabase((database) => runRequest<T[]>(
    database.transaction(storeName, 'readonly').objectStore(storeName).getAll(),
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
