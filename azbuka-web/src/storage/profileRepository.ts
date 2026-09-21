import type { UserProfile } from '@/domain/types'

const DATABASE_NAME = 'azbuka-web'
const DATABASE_VERSION = 1
const STORE_NAME = 'profile'
const PROFILE_KEY = 'primary'

export const defaultProfile = (): UserProfile => ({
  locale: 'cu',
  dailyGoal: 10,
  learnedLetterIds: [],
  answered: 0,
  correct: 0,
  todayAnswered: 0,
  todayDate: null,
  streak: 0,
  lastPracticeDate: null
})

const openDatabase = (): Promise<IDBDatabase> => new Promise((resolve, reject) => {
  const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains(STORE_NAME)) {
      request.result.createObjectStore(STORE_NAME)
    }
  }
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})

export const loadProfile = async (): Promise<UserProfile | null> => {
  const database = await openDatabase()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, 'readonly')
    const request = transaction.objectStore(STORE_NAME).get(PROFILE_KEY)
    request.onsuccess = () => {
      const stored = request.result as Partial<UserProfile> | undefined
      resolve(stored ? { ...defaultProfile(), ...stored } : null)
    }
    request.onerror = () => reject(request.error)
    transaction.oncomplete = () => database.close()
  })
}

export const saveProfile = async (profile: UserProfile): Promise<void> => {
  const database = await openDatabase()
  const snapshot = JSON.parse(JSON.stringify(profile)) as UserProfile
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, 'readwrite')
    transaction.objectStore(STORE_NAME).put(snapshot, PROFILE_KEY)
    transaction.oncomplete = () => {
      database.close()
      resolve()
    }
    transaction.onerror = () => reject(transaction.error)
  })
}

export const clearProfile = async (): Promise<void> => {
  const database = await openDatabase()
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(STORE_NAME, 'readwrite')
    transaction.objectStore(STORE_NAME).delete(PROFILE_KEY)
    transaction.oncomplete = () => {
      database.close()
      resolve()
    }
    transaction.onerror = () => reject(transaction.error)
  })
}
