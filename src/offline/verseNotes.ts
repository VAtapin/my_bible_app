import { offlineStores, openOfflineDatabase, runRequest } from './database'

export interface VerseNote { key: string; text: string; updatedAt: string }
const prefix = 'verse-note:'

/** Personal notes use the existing local state store, separate from downloaded texts. */
export function createVerseNoteRepository() {
  return {
    async get(key: string): Promise<VerseNote | undefined> {
      const database = await openOfflineDatabase()
      try {
        const record = await runRequest<{ value: VerseNote } | undefined>(database.transaction(offlineStores.state, 'readonly').objectStore(offlineStores.state).get(`${prefix}${key}`))
        return record?.value
      } finally { database.close() }
    },
    async save(note: VerseNote): Promise<void> {
      const database = await openOfflineDatabase()
      try {
        await runRequest(database.transaction(offlineStores.state, 'readwrite').objectStore(offlineStores.state).put({ key: `${prefix}${note.key}`, value: note }))
      } finally { database.close() }
    },
  }
}
