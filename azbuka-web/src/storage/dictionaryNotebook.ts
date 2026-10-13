import { openDatabase } from './profileRepository'

export interface SavedDictionaryWord { key:string; word:string; civil:string }
export interface DictionaryNotebook { favorites:SavedDictionaryWord[]; recent:SavedDictionaryWord[] }
const valid=(value:unknown):value is SavedDictionaryWord=>typeof value==='object'&&value!==null&&
 'key' in value&&typeof value.key==='string'&&/^[a-f0-9]{40}$/.test(value.key)&&
 'word' in value&&typeof value.word==='string'&&'civil' in value&&typeof value.civil==='string'
const words=(value:unknown)=>Array.isArray(value)?value.filter(valid):[]
export async function loadDictionaryNotebook():Promise<DictionaryNotebook>{
 const database=await openDatabase()
 return new Promise((resolve,reject)=>{
  const transaction=database.transaction('dictionary','readonly'),request=transaction.objectStore('dictionary').get('notebook')
  let result:DictionaryNotebook={favorites:[],recent:[]}
  request.onsuccess=()=>{const value=request.result;result={favorites:words(value?.favorites),recent:words(value?.recent).slice(0,30)}}
  transaction.oncomplete=()=>{database.close();resolve(result)}
  transaction.onabort=()=>{database.close();reject(transaction.error)}
  transaction.onerror=()=>{database.close();reject(transaction.error)}
 })
}
/** A read/write transaction prevents competing tabs from overwriting another favorite. */
async function updateNotebook(change:(value:DictionaryNotebook)=>DictionaryNotebook):Promise<DictionaryNotebook>{
 const database=await openDatabase()
 return new Promise((resolve,reject)=>{
  const transaction=database.transaction('dictionary','readwrite'),store=transaction.objectStore('dictionary'),request=store.get('notebook')
  let result:DictionaryNotebook
  request.onsuccess=()=>{const value=request.result;result=change({favorites:words(value?.favorites),recent:words(value?.recent)});store.put(JSON.parse(JSON.stringify(result)),'notebook')}
  transaction.oncomplete=()=>{database.close();resolve(result!)}
  transaction.onabort=()=>{database.close();reject(transaction.error)}
  transaction.onerror=()=>{database.close();reject(transaction.error)}
 })
}
export const rememberDictionaryWord=(word:SavedDictionaryWord)=>updateNotebook(value=>({...value,recent:[word,...value.recent.filter(item=>item.key!==word.key)].slice(0,30)}))
export const toggleDictionaryFavorite=(word:SavedDictionaryWord)=>updateNotebook(value=>({...value,favorites:value.favorites.some(item=>item.key===word.key)?value.favorites.filter(item=>item.key!==word.key):[word,...value.favorites]}))
