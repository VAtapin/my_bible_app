import 'fake-indexeddb/auto'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
  offlineDatabaseName,
  offlineDatabaseVersion,
  offlineStores,
  openOfflineDatabase,
  runRequest,
} from './database'

beforeEach(async () => deleteDatabase())
afterEach(async () => deleteDatabase())

describe('IndexedDB migrations', () => {
  it('upgrades schema 1 to the current version without losing chapters', async () => {
    const versionOne = await openVersionOne()
    await runRequest(versionOne.transaction('chapters', 'readwrite').objectStore('chapters').put({
      key: 'RST:genesis:1',
      savedAt: '2026-09-18',
      data: { book: 'Genesis' },
    }))
    versionOne.close()

    const upgraded = await openOfflineDatabase()
    expect(upgraded.version).toBe(offlineDatabaseVersion)
    expect([...upgraded.objectStoreNames]).toEqual(expect.arrayContaining(Object.values(offlineStores)))
    const chapter = await runRequest<{ key: string }>(
      upgraded.transaction(offlineStores.chapters, 'readonly').objectStore(offlineStores.chapters).get('RST:genesis:1'),
    )
    expect(chapter.key).toBe('RST:genesis:1')
    upgraded.close()
  })
})

function openVersionOne(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(offlineDatabaseName, 1)
    request.onupgradeneeded = () => request.result.createObjectStore('chapters', { keyPath: 'key' })
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

function deleteDatabase(): Promise<void> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.deleteDatabase(offlineDatabaseName)
    request.onsuccess = () => resolve()
    request.onerror = () => reject(request.error)
    request.onblocked = () => reject(new Error('IndexedDB deletion was blocked.'))
  })
}
