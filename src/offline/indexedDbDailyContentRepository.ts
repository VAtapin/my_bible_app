import type { DailyContentRepository, StoredCalendarDay, StoredPrayer, StoredPrayerCatalog,StoredLiturgicalVersion } from './dailyContentRepository'
import type {LiturgicalWorkSummary} from '@/api/contracts'
import {ApiError,isPrayerSummary,isPrayerDetail,isReviewedPrayerDetail,isLiturgicalWork,isLiturgicalWorkVersion,isReviewedLiturgicalVersion} from '@/api/client'
import {isPrayerCatalog} from '@/api/prayerCatalog'
import {isCompletePrayer} from '@/services/prayerCatalog'
import { offlineStores, openOfflineDatabase, runRequest } from './database'

export function createIndexedDbDailyContentRepository(): DailyContentRepository {
  return {
    async getPrayer(id,language) {
      if((await read<{value:boolean}>(offlineStores.state,`prayer-blocked:${id}:${language??''}`))?.value)return undefined
      const alias=await read<{key:string;target:string}>(offlineStores.state,`prayer-alias:${id}:${language??''}`)
      const value=await read<StoredPrayer>(offlineStores.prayers,alias?.target??String(id))
      if(value&&isPrayerDetail(value.data)&&(await read<{value:boolean}>(offlineStores.state,`prayer-blocked:${id}:${value.data.language_code}`))?.value)return undefined
      const reviewed=await read<{value:boolean}>(offlineStores.state,'prayer-reviewed-v2')
      return value&&isPrayerDetail(value.data)&&(!value.data.canonical_slug||isReviewedPrayerDetail(value.data))&&(!reviewed?.value||isCompletePrayer(value.data))&&(!language||value.data.language_code===language)?value:undefined
    },
    async putPrayer(value) {
      const data=value.data
      if(!isPrayerDetail(data)||data.canonical_slug&&!isReviewedPrayerDetail(data))throw new ApiError('invalid-response','Invalid saved prayer')
      if(data.canonical_slug&&data.content_revision&&data.completeness==='complete'){
        const key=`canonical-prayer:${data.canonical_slug}:${data.language_code}`
        await withDatabase(database=>transaction(database,[offlineStores.prayers,offlineStores.state],tx=>{
          const rows=tx.objectStore(offlineStores.prayers).getAll()
          rows.onsuccess=()=>{for(const old of rows.result as StoredPrayer[])if(old.key!==key&&(!isPrayerDetail(old.data)||!isCompletePrayer(old.data)))tx.objectStore(offlineStores.prayers).delete(old.key)}
          tx.objectStore(offlineStores.prayers).put({...value,key})
          tx.objectStore(offlineStores.state).put({key:'prayer-reviewed-v2',value:true})
          for(const language of ['',data.language_code])tx.objectStore(offlineStores.state).put({key:`prayer-alias:${data.id}:${language}`,target:key})
          for(const language of ['',data.language_code])tx.objectStore(offlineStores.state).delete(`prayer-blocked:${data.id}:${language}`)
        }))
      }else await withDatabase(database=>transaction(database,[offlineStores.prayers,offlineStores.state],tx=>{
        tx.objectStore(offlineStores.prayers).put(value)
        for(const language of ['',data.language_code])tx.objectStore(offlineStores.state).delete(`prayer-blocked:${data.id}:${language}`)
      }))
    },
    async invalidatePrayer(id,language){
      const saved=language?undefined:await this.getPrayer(id)
      await withDatabase(database=>transaction(database,[offlineStores.state],tx=>{
        const state=tx.objectStore(offlineStores.state)
        state.put({key:`prayer-blocked:${id}:${language??''}`,value:true})
        // A default compatibility response concerns the actual cached edition, not every language.
        if(saved)state.put({key:`prayer-blocked:${id}:${saved.data.language_code}`,value:true})
      }))
    },
    async listPrayers() {
      const reviewed=await read<{value:boolean}>(offlineStores.state,'prayer-reviewed-v2')
      return(await list<StoredPrayer>(offlineStores.prayers)).filter(row=>isPrayerDetail(row.data)&&(!row.data.canonical_slug||isReviewedPrayerDetail(row.data))&&(!reviewed?.value||isCompletePrayer(row.data)))
    },
    getCalendarDay(date) {
      return read<StoredCalendarDay>(offlineStores.calendar, date)
    },
    putCalendarDay(value) {
      return put(offlineStores.calendar, value)
    },
    listCalendarDays() {
      return list<StoredCalendarDay>(offlineStores.calendar)
    },
    async getPrayerCatalog(){const value=await read<StoredPrayerCatalog>(offlineStores.state,'prayer-catalog');return value&&isPrayerCatalog(value.data,isPrayerSummary)?value:undefined},
    async isReviewedPrayerCatalog(){return Boolean((await read<{value:boolean}>(offlineStores.state,'prayer-reviewed-v2'))?.value)},
    async putPrayerCatalog(value){
      await withDatabase(database=>new Promise<void>((resolve,reject)=>{
        const tx=database.transaction([offlineStores.prayers,offlineStores.state],'readwrite')
        tx.oncomplete=()=>resolve();tx.onerror=tx.onabort=()=>reject(tx.error??new Error('Storage failed'))
        const state=tx.objectStore(offlineStores.state)
        // The confirmed catalogue transition affects public prayer caches only; personal stores stay untouched.
        if(value.data.catalog_version===2){
          state.put({key:'prayer-reviewed-v2',value:true})
          const bySlug=new Map(value.data.data.map(item=>[item.canonical_slug,item]))
          const hiddenEditions=new Set<string>()
          const valid=(data:StoredPrayer['data'])=>{
            if(!isReviewedPrayerDetail(data))return false
            const entry=bySlug.get(data.canonical_slug)
            if(!entry)return data.catalog_visible===false
            return Boolean(data.canonical_slug&&data.content_revision&&data.completeness==='complete'&&entry&&entry.available_languages?.includes(data.language_code)&&
              (entry.language_code!==data.language_code||entry.content_revision===data.content_revision))
          }
          const prayers=tx.objectStore(offlineStores.prayers)
          const rows=prayers.getAll();rows.onsuccess=()=>{for(const row of rows.result as StoredPrayer[]){
            if(!valid(row.data))prayers.delete(row.key)
            else if(row.data.catalog_visible===false)hiddenEditions.add(`${row.data.canonical_slug}:${row.data.language_code}:${row.data.content_revision}`)
          }}
          const versions=state.openCursor(IDBKeyRange.bound('prayer-version:','prayer-version:\uffff'))
          versions.onsuccess=()=>{
            const cursor=versions.result;if(!cursor)return
            const row=cursor.value as StoredLiturgicalVersion
            if(row.collection==='prayers'){
              if(!isLiturgicalWorkVersion(row.data)){cursor.delete();cursor.continue();return}
              const entry=bySlug.get(row.data.slug)
              const hidden=hiddenEditions.has(`${row.data.slug}:${row.data.language}:${row.data.content_hash}`)
              if(!isReviewedLiturgicalVersion(row.data)||(!entry?!hidden:!entry.available_languages?.includes(row.data.language)||entry.language_code===row.data.language&&entry.content_revision!==row.data.content_hash))cursor.delete()
            }
            cursor.continue()
          }
        }
        state.put({...value,key:'prayer-catalog'})
      }))
    },
    async getLiturgicalWork(slug){
      const alias=await read<{target:string}>(offlineStores.state,`prayer-work-alias:${slug}`)
      const data=(await read<{data:LiturgicalWorkSummary}>(offlineStores.state,`prayer-work:${alias?.target??slug}`))?.data
      return data&&isLiturgicalWork(data)&&(data.slug===slug||data.legacy_slugs?.includes(slug))?data:undefined
    },
    async putLiturgicalWork(value){
      await withDatabase(database=>transaction(database,[offlineStores.state],tx=>{
        const state=tx.objectStore(offlineStores.state);state.put({key:`prayer-work:${value.slug}`,data:value})
        for(const alias of value.legacy_slugs??[])state.put({key:`prayer-work-alias:${alias}`,target:value.slug})
      }))
    },
    async getLiturgicalVersion(slug,language,edition){
      if((await read<{value:boolean}>(offlineStores.state,`prayer-version-blocked:${slug}:${language}:${edition??''}`))?.value)return undefined
      const work=await this.getLiturgicalWork?.(slug)
      const canonical=work?.slug??slug
      if((await read<{value:boolean}>(offlineStores.state,`prayer-version-blocked:${canonical}:${language}:${edition??''}`))?.value)return undefined
      const alias=await read<{target:string}>(offlineStores.state,`prayer-version-alias:${slug}:${language}:${edition??''}`)??
        await read<{target:string}>(offlineStores.state,`prayer-version-alias:${canonical}:${language}:${edition??''}`)
      const value=await read<StoredLiturgicalVersion>(offlineStores.state,alias?.target??`prayer-version:${canonical}:${language}:${edition??''}`)
      if(!value||!isLiturgicalWorkVersion(value.data)||value.data.slug!==canonical||value.data.language!==language||edition&&value.data.edition!==edition||
        value.data.completeness==='complete'&&!isReviewedLiturgicalVersion(value.data,language,edition))return undefined
      if((await read<{value:boolean}>(offlineStores.state,`prayer-version-blocked:${canonical}:${language}:${value.data.edition}`))?.value)return undefined
      return value
    },
    async invalidateLiturgicalVersion(slug,language,edition){
      const saved=await this.getLiturgicalVersion?.(slug,language,edition)
      const work=await this.getLiturgicalWork?.(slug)
      const canonical=work?.slug??saved?.data.slug??slug
      await withDatabase(database=>transaction(database,[offlineStores.state],tx=>{
        const state=tx.objectStore(offlineStores.state)
        for(const target of new Set([slug,canonical])){
          state.put({key:`prayer-version-blocked:${target}:${language}:${edition??''}`,value:true})
          if(saved)state.put({key:`prayer-version-blocked:${target}:${language}:${saved.data.edition}`,value:true})
        }
      }))
    },
    async putLiturgicalVersion(value,requestedSlug,requestedEdition){
      const data=value.data,key=`prayer-version:${data.slug}:${data.language}:${data.edition}`
      if(!isLiturgicalWorkVersion(data)||data.completeness==='complete'&&!isReviewedLiturgicalVersion(data,data.language,requestedEdition))throw new ApiError('invalid-response','Invalid saved prayer edition')
      await withDatabase(database=>transaction(database,[offlineStores.state,offlineStores.prayers],tx=>{
        const state=tx.objectStore(offlineStores.state);state.put({...value,key})
        if(value.collection==='prayers'&&data.completeness==='complete'){
          state.put({key:'prayer-reviewed-v2',value:true})
          const prayers=tx.objectStore(offlineStores.prayers),rows=prayers.getAll()
          rows.onsuccess=()=>{for(const row of rows.result as StoredPrayer[])if(!isPrayerDetail(row.data)||!isCompletePrayer(row.data))prayers.delete(row.key)}
        }
        for(const slug of new Set([requestedSlug,data.slug]))state.put({key:`prayer-version-alias:${slug}:${data.language}:${requestedEdition??''}`,target:key})
        for(const slug of new Set([requestedSlug,data.slug]))for(const edition of new Set([requestedEdition??'',data.edition]))state.delete(`prayer-version-blocked:${slug}:${data.language}:${edition}`)
      }))
    },
  }
}

function transaction(database:IDBDatabase,stores:string[],action:(tx:IDBTransaction)=>void):Promise<void>{
  return new Promise((resolve,reject)=>{const tx=database.transaction(stores,'readwrite');tx.oncomplete=()=>resolve();tx.onerror=tx.onabort=()=>reject(tx.error??new Error('Storage failed'));action(tx)})
}

async function read<T>(storeName: string, key: string): Promise<T | undefined> {
  return withDatabase((database) => runRequest<T | undefined>(
    database.transaction(storeName, 'readonly').objectStore(storeName).get(key),
  ))
}

async function put(storeName: string, value: unknown): Promise<void> {
  await withDatabase((database) => new Promise<void>((resolve, reject) => {
    const transaction = database.transaction(storeName, 'readwrite')
    transaction.oncomplete = () => resolve()
    transaction.onerror = transaction.onabort = () => reject(transaction.error ?? new Error('Storage failed'))
    transaction.objectStore(storeName).put(value)
  }))
}

async function list<T>(storeName: string): Promise<T[]> {
  return withDatabase((database) => runRequest<T[]>(
    database.transaction(storeName, 'readonly').objectStore(storeName).getAll(),
  ))
}

async function withDatabase<T>(action: (database: IDBDatabase) => Promise<T>): Promise<T> {
  const database = await openOfflineDatabase()
  try {
    return await action(database)
  } finally {
    database.close()
  }
}
