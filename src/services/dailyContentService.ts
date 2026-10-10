import type { BibleApi } from '@/api/client'
import {ApiError} from '@/api/client'
import type { CalendarDay,PrayerCatalog,LiturgicalWorkSummary } from '@/api/contracts'
import type { DailyContentRepository } from '@/offline/dailyContentRepository'
import { addCalendarDays } from './calendarDates'
import { linkedPrayerEdition, type PresentedPrayer } from './prayerEditions'
import { apiBaseUrl } from '@/config/api'
import {isCompletePrayer,uniquePrayerCards} from './prayerCatalog'

export function prayerNetworkUnavailable(error:unknown):boolean{return error instanceof ApiError&&['offline','timeout'].includes(error.kind)}
function finalPrayerRejection(error:unknown):boolean{return error instanceof ApiError&&error.kind==='http'&&[404,409].includes(error.status??0)}

export function createDailyContentService(api: BibleApi, repository: DailyContentRepository) {
  const iconMappings = new Map<number, string[]>()
  return {
    async openPrayerCatalog():Promise<{data:PrayerCatalog;offline:boolean}>{
      try{
        const data:PrayerCatalog=api.getPrayerCatalog?await api.getPrayerCatalog('ru'):{data:await api.getPrayers('ru')}
        if((await repository.isReviewedPrayerCatalog?.()||(await repository.getPrayerCatalog?.())?.data.catalog_version===2)&&data.catalog_version!==2)throw new ApiError('invalid-response','Confirmed prayer catalogue cannot downgrade')
        const catalog={...data,data:uniquePrayerCards(data.data)}
        await repository.putPrayerCatalog?.({key:'prayer-catalog',savedAt:new Date().toISOString(),data:catalog})
        return{data:catalog,offline:false}
      }catch(error){
        if(!prayerNetworkUnavailable(error))throw error
        const saved=await repository.getPrayerCatalog?.()
        if(saved)return{data:saved.data,offline:true}
        if(await repository.isReviewedPrayerCatalog?.())throw new ApiError('invalid-response','Saved prayer catalogue is unavailable')
        const prayers=await repository.listPrayers()
        if(!prayers.length)throw error
        return{data:{data:uniquePrayerCards(prayers.map(({data})=>({...data,excerpt:data.intro??null})))},offline:true}
      }
    },
    async openPrayer(id: number,language?:string): Promise<{ data: PresentedPrayer; offline: boolean }> {
      try {
        const data: PresentedPrayer = { ...await api.getPrayer(id,language) }
        if(data.id!==id||language&&data.language_code!==language)throw new ApiError('invalid-response','Wrong prayer identity')
        if((await repository.isReviewedPrayerCatalog?.()||(await repository.getPrayerCatalog?.())?.data.catalog_version===2)&&!isCompletePrayer(data))throw new ApiError('invalid-response','Confirmed prayer edition is unavailable in this response')
        try {
          if (!data.canonical_slug&&typeof api.getLiturgicalWorks === 'function') {
            const edition = linkedPrayerEdition(id, await api.getLiturgicalWorks('prayers') ?? [], apiBaseUrl)
            if (edition) data.text_edition = edition
          }
        } catch { /* A catalogue failure must not hide a prayer or guess its language. */ }
        await repository.putPrayer({ key: String(id), savedAt: new Date().toISOString(), data })
        return { data, offline: false }
      } catch (networkError) {
        if(finalPrayerRejection(networkError))await repository.invalidatePrayer?.(id,language)
        if(!prayerNetworkUnavailable(networkError))throw networkError
        const stored = await repository.getPrayer(id,language)
        if (!stored) throw networkError
        const catalog=(await repository.getPrayerCatalog?.())?.data
        if(await repository.isReviewedPrayerCatalog?.()&&!isCompletePrayer(stored.data))throw new ApiError('invalid-response','Saved prayer edition is outdated')
        if(catalog?.catalog_version===2){
          const entry=catalog.data.find(item=>item.canonical_slug===stored.data.canonical_slug)
          if(!isCompletePrayer(stored.data)||(!entry?stored.data.catalog_visible!==false:!entry.available_languages?.includes(stored.data.language_code)||entry.language_code===stored.data.language_code&&entry.content_revision!==stored.data.content_revision))
            throw new ApiError('invalid-response','Saved prayer edition is outdated')
        }
        return { data: stored.data, offline: true }
      }
    },
    async openLiturgicalVersion(slug:string,language:string,edition?:string){
      let metadata:LiturgicalWorkSummary|undefined
      try{
        metadata=await api.getLiturgicalWork?.(slug)
        if(metadata&&metadata.slug!==slug&&!metadata.legacy_slugs?.includes(slug))throw new ApiError('invalid-response','Wrong prayer identity')
        if(metadata)await repository.putLiturgicalWork?.(metadata)
      }catch(error){
        if(finalPrayerRejection(error))await repository.invalidateLiturgicalVersion?.(slug,language,edition)
        if(!prayerNetworkUnavailable(error))throw error;metadata=await repository.getLiturgicalWork?.(slug)
      }
      const canonical=metadata?.slug??slug
      if(metadata&&!metadata.available_languages.includes(language)){
        await repository.invalidateLiturgicalVersion?.(slug,language,edition)
        throw new ApiError('http','Prayer edition unavailable',404)
      }
      try{
        const data=await api.getLiturgicalVersion(canonical,language,edition)
        if(data.slug!==canonical||data.language!==language||edition&&data.edition!==edition)throw new ApiError('invalid-response','Wrong prayer edition identity')
        const catalog=(await repository.getPrayerCatalog?.())?.data
        const reviewedPrayer=metadata?.collections.includes('prayers')||catalog?.data.some(item=>item.canonical_slug===data.slug)
        if((await repository.isReviewedPrayerCatalog?.()||catalog?.catalog_version===2)&&reviewedPrayer&&data.completeness!=='complete')throw new ApiError('invalid-response','Confirmed prayer edition is unavailable in this response')
        await repository.putLiturgicalVersion?.({key:'',savedAt:new Date().toISOString(),data,workRevision:metadata?.content_revision,collection:metadata?.collections.includes('prayers')?'prayers':undefined},slug,edition)
        return{data,metadata,offline:false}
      }catch(error){
        if(finalPrayerRejection(error))await repository.invalidateLiturgicalVersion?.(slug,language,edition)
        if(!prayerNetworkUnavailable(error))throw error
        const saved=await repository.getLiturgicalVersion?.(slug,language,edition)
        if(!saved)throw error
        if(saved.data.slug!==canonical||saved.data.language!==language||edition&&saved.data.edition!==edition)throw new ApiError('invalid-response','Wrong cached prayer identity')
        const catalog=(await repository.getPrayerCatalog?.())?.data
        const entry=catalog?.data.find(item=>item.canonical_slug===saved.data.slug)
        const hidden=!entry&&catalog?.catalog_version===2&&(await repository.listPrayers()).some(({data})=>data.catalog_visible===false&&data.canonical_slug===saved.data.slug&&data.language_code===saved.data.language&&data.content_revision===saved.data.content_hash)
        if(metadata?.content_revision&&metadata.content_revision!==saved.workRevision||
          (await repository.isReviewedPrayerCatalog?.()||catalog?.catalog_version===2)&&saved.collection==='prayers'&&saved.data.completeness!=='complete'||
          catalog?.catalog_version===2&&saved.collection==='prayers'&&(!entry?!hidden:!entry.available_languages?.includes(saved.data.language)||entry.language_code===saved.data.language&&entry.content_revision!==saved.data.content_hash))throw new ApiError('invalid-response','Saved prayer edition is outdated')
        return{data:saved.data,metadata,offline:true}
      }
    },
    async openCalendarDay(date: string, language = 'ru'): Promise<{ data: CalendarDay; offline: boolean }> {
      try {
        const data = await api.getCalendarDay(date, language)
        if ((data.icons?.length ?? 0) > 1) {
          await Promise.all(data.icons!.map(async (icon) => {
            if (icon.calendar_record_ids) return
            try {
              const ids = iconMappings.get(icon.id) ?? (await api.getCalendarIcon(icon.id)).calendarRecordIds
              iconMappings.set(icon.id, ids)
              icon.calendar_record_ids = ids
            } catch { /* A missing catalogue must not hide the actual day or its icons. */ }
          }))
        }
        await repository.putCalendarDay({ key: `${language}:${date}`, savedAt: new Date().toISOString(), data })
        return { data, offline: false }
      } catch (networkError) {
        const stored = await repository.getCalendarDay(`${language}:${date}`) ?? await repository.getCalendarDay(date)
        if (!stored) throw networkError
        return { data: stored.data, offline: true }
      }
    },
    async downloadCalendarHorizon(
      startDate: string,
      days: number,
      onProgress: (current: number, total: number) => void,
      signal?: AbortSignal,
      language = 'ru',
    ): Promise<void> {
      for (let index = 0; index < days; index += 1) {
        if (signal?.aborted) throw new DOMException('Загрузка остановлена.', 'AbortError')
        const date = addCalendarDays(startDate, index)
        const data = await api.getCalendarDay(date, language)
        await repository.putCalendarDay({ key: `${language}:${date}`, savedAt: new Date().toISOString(), data })
        onProgress(index + 1, days)
      }
    },
  }
}
