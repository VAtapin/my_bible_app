const databaseName = 'my-bible-app'
const databaseVersion = 2

export const offlineStores = {
  chapters: 'chapters',
  bookmarks: 'bookmarks',
  state: 'state',
} as const

export function openOfflineDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(databaseName, databaseVersion)

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
