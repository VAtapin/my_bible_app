import type { ChapterRepository, StoredChapter } from './chapterRepository'

const databaseName = 'my-bible-app'
const storeName = 'chapters'

export function createIndexedDbChapterRepository(): ChapterRepository {
  const openDatabase = (): Promise<IDBDatabase> =>
    new Promise((resolve, reject) => {
      const request = indexedDB.open(databaseName, 1)

      request.onupgradeneeded = () => {
        const database = request.result
        if (!database.objectStoreNames.contains(storeName)) {
          database.createObjectStore(storeName, { keyPath: 'key' })
        }
      }
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })

  return {
    async get(key) {
      const database = await openDatabase()
      try {
        return await runRequest<StoredChapter | undefined>(
          database.transaction(storeName, 'readonly').objectStore(storeName).get(key),
        )
      } finally {
        database.close()
      }
    },
    async put(chapter) {
      const database = await openDatabase()
      try {
        await runRequest(database.transaction(storeName, 'readwrite').objectStore(storeName).put(chapter))
      } finally {
        database.close()
      }
    },
  }
}

function runRequest<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}
