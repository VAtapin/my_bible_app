import { offlineStores, openOfflineDatabase, runRequest } from './database'
import { emptyPersonalStudy, type PersonalStudy } from '@/services/personalStudy'
const key = 'personal-study:v1'
function validate(value: PersonalStudy): PersonalStudy {
  if (value.version !== 1 || !Array.isArray(value.bookmarks) || !Array.isArray(value.cards) || !Array.isArray(value.marks) || typeof value.palette !== 'object') throw new Error('Invalid personal study data')
  return value
}
export function createPersonalStudyRepository() {
  return {
    async read(): Promise<PersonalStudy> {
      const db = await openOfflineDatabase()
      try { const record = await runRequest<{ value: PersonalStudy } | undefined>(db.transaction(offlineStores.state, 'readonly').objectStore(offlineStores.state).get(key)); return record ? validate(record.value) : emptyPersonalStudy() }
      finally { db.close() }
    },
    async update(transform: (value: PersonalStudy) => PersonalStudy): Promise<PersonalStudy> {
      const db = await openOfflineDatabase()
      try {
        return await new Promise((resolve, reject) => {
          const transaction = db.transaction(offlineStores.state, 'readwrite'), store = transaction.objectStore(offlineStores.state), request = store.get(key)
          let result: PersonalStudy
          request.onsuccess = () => {
            try { result = validate(transform(request.result ? validate(request.result.value) : emptyPersonalStudy())); store.put({ key, value: JSON.parse(JSON.stringify(result)) }) }
            catch (error) { transaction.abort(); reject(error) }
          }
          transaction.oncomplete = () => { if(typeof window !== 'undefined')window.dispatchEvent(new Event('personal-study-changed')); resolve(result) }
          transaction.onerror = () => reject(transaction.error)
          transaction.onabort = () => reject(transaction.error ?? new Error('Write aborted'))
        })
      } finally { db.close() }
    },
  }
}
