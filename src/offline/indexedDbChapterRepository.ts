import type { ChapterRepository, StoredChapter } from './chapterRepository'
import { offlineStores, openOfflineDatabase, runRequest } from './database'

export function createIndexedDbChapterRepository(): ChapterRepository {
  return {
    async get(key) {
      const database = await openOfflineDatabase()
      try {
        return await runRequest<StoredChapter | undefined>(
          database.transaction(offlineStores.chapters, 'readonly').objectStore(offlineStores.chapters).get(key),
        )
      } finally {
        database.close()
      }
    },
    async put(chapter) {
      const database = await openOfflineDatabase()
      try {
        await runRequest(
          database.transaction(offlineStores.chapters, 'readwrite').objectStore(offlineStores.chapters).put(chapter),
        )
      } finally {
        database.close()
      }
    },
    async list() {
      const database = await openOfflineDatabase()
      try {
        return await runRequest<StoredChapter[]>(
          database.transaction(offlineStores.chapters, 'readonly').objectStore(offlineStores.chapters).getAll(),
        )
      } finally {
        database.close()
      }
    },
    async delete(key) {
      const database = await openOfflineDatabase()
      try {
        await runRequest(
          database.transaction(offlineStores.chapters, 'readwrite').objectStore(offlineStores.chapters).delete(key),
        )
      } finally {
        database.close()
      }
    },
  }
}
