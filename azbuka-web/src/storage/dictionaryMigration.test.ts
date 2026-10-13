import 'fake-indexeddb/auto'
import {expect,it} from 'vitest'
import {defaultProfile,loadProfile} from './profileRepository'
import {loadDictionaryNotebook} from './dictionaryNotebook'
it('adds dictionary storage to a real version-one database without losing the existing learning profile',async()=>{
 const profile={...defaultProfile(),learnedLetterIds:['az'],answered:42}
 await new Promise<void>((resolve,reject)=>{
  const request=indexedDB.open('azbuka-web',1)
  request.onupgradeneeded=()=>request.result.createObjectStore('profile')
  request.onsuccess=()=>{const database=request.result,transaction=database.transaction('profile','readwrite');transaction.objectStore('profile').put(profile,'primary');transaction.oncomplete=()=>{database.close();resolve()};transaction.onerror=()=>reject(transaction.error)}
  request.onerror=()=>reject(request.error)
 })
 expect(await loadDictionaryNotebook()).toEqual({favorites:[],recent:[]})
 expect(await loadProfile()).toEqual(profile)
})
