export const offlineDatabaseName = 'my-bible-app'
export const offlineDatabaseVersion = 3

export const offlineStores = {
  chapters: 'chapters',
  bookmarks: 'bookmarks',
  state: 'state',
  prayers: 'prayers',
  calendar: 'calendar',
} as const

export function openOfflineDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(offlineDatabaseName, offlineDatabaseVersion)

    request.onupgradeneeded = () => {
      const database = request.result
      if (!database.objectStoreNames.contains(offlineStores.chapters)) {
        database.createObjectStore(offlineStores.chapters, { keyPath: 'key' })
      }
      if (!database.objectStoreNames.contains(offlineStores.bookmarks)) {
        database.createObjectStore(offlineStores.bookmarks, { keyPath: 'key' })
      }
      if (!database.objectStoreNames.contains(offlineStores.state)) {
        database.createObjectStore(offlineStores.state, { keyPath: 'key' })
      }
      if (!database.objectStoreNames.contains(offlineStores.prayers)) {
        database.createObjectStore(offlineStores.prayers, { keyPath: 'key' })
      }
      if (!database.objectStoreNames.contains(offlineStores.calendar)) {
        database.createObjectStore(offlineStores.calendar, { keyPath: 'key' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

export function runRequest<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}
