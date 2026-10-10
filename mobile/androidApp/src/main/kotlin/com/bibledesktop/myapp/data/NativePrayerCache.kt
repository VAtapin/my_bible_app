package com.bibledesktop.myapp.data

import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable internal data class CachedPrayerIdentity(val canonical:String,val workId:Long,val language:String,val revision:String,val aliases:List<Long>,val visible:Boolean)
@Serializable internal data class PrayerCacheIndex(val schema:Int,val entries:List<CachedPrayerIdentity>)
internal fun prayerEditionCacheKey(value:CachedPrayerIdentity)="prayer-reviewed:${value.canonical}:${value.language}:${value.revision}"

/** Catalog v2 invalidates legacy bodies logically, without deleting personal/calendar/Bible data. */
internal class NativePrayerCache(private val remote:BibleContentSource,private val store:OfflineStore,private val online:()->Boolean, private val unavailable:(Throwable)->Boolean=::isPrayerUnavailableFailure){
    companion object {private val writes=Mutex();private const val marker="prayer-catalog-confirmed-version";private const val identities="prayer-reviewed-identities";private const val denied="prayer-unavailable-requests"}
    private suspend fun reviewed()=store.read(marker,Int.serializer())==2 || store.read(identities,PrayerCacheIndex.serializer())?.schema==2
    private suspend fun index()=store.read(identities,PrayerCacheIndex.serializer())?.takeIf{it.schema in 1..2}?.entries.orEmpty()
    private fun identity(value:PrayerSummary):CachedPrayerIdentity {
        PrayerCatalog(listOf(value),2,mapOf("short" to "short","rules" to "rules","occasions" to "occasions","initial" to "initial")).validatePrayerCatalog()
        require(value.id>0&&value.languageCode in value.availableLanguages)
        return CachedPrayerIdentity(value.canonicalSlug!!,value.liturgicalWorkId!!,value.languageCode,value.contentRevision!!,listOf(value.id),value.catalogVisible!=false)
    }
    private fun identity(value:PrayerDetail):CachedPrayerIdentity {
        value.validatePrayerDetail(2)
        require(value.id>0&&value.body.isNotBlank()&&value.languageCode in value.availableLanguages)
        return CachedPrayerIdentity(value.canonicalSlug!!,value.liturgicalWorkId!!,value.languageCode,value.contentRevision!!,listOf(value.id),value.catalogVisible!=false)
    }
    private fun merge(previous:List<CachedPrayerIdentity>,fresh:List<CachedPrayerIdentity>):List<CachedPrayerIdentity>{
        val affected=fresh.map{it.canonical to it.language}.toSet()
        return previous.filterNot{it.canonical to it.language in affected}+fresh.map{entry->entry.copy(aliases=(entry.aliases+previous.filter{it.canonical==entry.canonical&&it.language==entry.language}.flatMap{it.aliases}).distinct())}
    }
    suspend fun catalog(language:String):PrayerCatalog {
        val key="prayer-catalog:$language"
        val confirmed=reviewed()
        val persisted=store.read(key,PrayerCatalog.serializer())
        val cached=persisted?.let{runCatching{it.validatePrayerCatalog()}.getOrNull()}?.takeIf{!confirmed||it.catalogVersion==2}
        if(persisted?.catalogVersion==2&&cached==null&&!online())error("The saved reviewed prayer catalogue is invalid")
        // A validated v2 record is itself proof, including after an interrupted AtomicFile sequence.
        if(cached?.catalogVersion==2&&!confirmed)confirmCatalog(key,cached)
        if(cached!=null&&!online())return cached
        val value=try{remote.getPrayerCatalog(language).validatePrayerCatalog()}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){
            if(!isPrayerTransportFailure(error))throw error
            if(cached!=null)return cached
            if(!reviewed())store.read("prayers:$language",ListSerializer(PrayerSummary.serializer()))?.let{return PrayerCatalog(data=it)}
            throw error
        }
        if(reviewed())require(value.catalogVersion==2){"The reviewed catalog cannot be replaced by a legacy response"}
        if(value.catalogVersion==2)confirmCatalog(key,value)
        else store.write(key,PrayerCatalog.serializer(),value)
        return value
    }
    private suspend fun confirmCatalog(key:String,value:PrayerCatalog){
        val fresh=value.data.map(::identity)
        writes.withLock{
            // schema 2 confirms review atomically together with per-edition identities.
            store.write(identities,PrayerCacheIndex.serializer(),PrayerCacheIndex(2,merge(index().filter{entry->
                // Only the compatible RU catalogue is the full visible inventory.
                // Other language queries cannot revoke different editions or hidden works.
                key!="prayer-catalog:ru"||!entry.visible||value.data.any{it.canonicalSlug==entry.canonical&&entry.language in it.availableLanguages}
            },fresh)))
            store.write(key,PrayerCatalog.serializer(),value)
            store.write(marker,Int.serializer(),2)
        }
    }
    private fun requestKey(id:Long,language:String?)="$id:${language?:"*"}"
    private suspend fun deniedRequests()=store.read(denied,ListSerializer(String.serializer())).orEmpty()
    private suspend fun isDenied(id:Long,language:String?)=deniedRequests().let{requestKey(id,null) in it||requestKey(id,language) in it}
    private suspend fun revoke(id:Long,language:String?)=writes.withLock{
        store.write(denied,ListSerializer(String.serializer()),(deniedRequests()+requestKey(id,language)).distinct())
    }
    private suspend fun restore(id:Long,language:String)=writes.withLock{
        store.write(denied,ListSerializer(String.serializer()),deniedRequests().filterNot{it==requestKey(id,null)||it==requestKey(id,language)})
    }
    suspend fun detail(id:Long,language:String?=null):PrayerDetail {
        require(id>0)
        if(!online()&&isDenied(id,language))error("This prayer request is unavailable")
        val entries=index().filter{id in it.aliases}
        val expected=if(language!=null)entries.firstOrNull{it.language==language}else entries.firstOrNull{it.language=="ru"}?:entries.firstOrNull{it.language=="cu-civil"}
        val saved=expected?.let{entry->store.read(prayerEditionCacheKey(entry),PrayerDetail.serializer())?.takeIf{runCatching{identity(it).let{actual->actual.canonical==entry.canonical&&actual.workId==entry.workId&&actual.language==entry.language&&actual.revision==entry.revision}}.getOrDefault(false)}}
        if(saved!=null&&!online())return saved.copy(id=id)
        if(!reviewed()&&!online())store.read("prayer:$id",PrayerDetail.serializer())?.takeIf{language==null||it.languageCode==language}?.let{return it}
        val value=try{(if(language!=null)remote.getPrayer(id,language)else remote.getPrayer(id)).validatePrayerDetail()}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){
            if(unavailable(error))revoke(id,language)
            if(!isPrayerTransportFailure(error))throw error
            if(!isDenied(id,language)){
                if(saved!=null)return saved.copy(id=id)
                if(!reviewed())store.read("prayer:$id",PrayerDetail.serializer())?.takeIf{language==null||it.languageCode==language}?.let{return it}
            }
            throw error
        }
        require(value.id==id)
        if(language!=null)require(value.languageCode==language){"The requested prayer edition is unavailable"}
        if(value.canonicalSlug!=null||value.completeness!=null||reviewed()){
            val actual=identity(value)
            writes.withLock{
                val current=index()
                // The validated current API detail is authoritative for this exact edition;
                // a stale local catalogue must not block a direct legacy numeric link.
                store.write(identities,PrayerCacheIndex.serializer(),PrayerCacheIndex(2,merge(current,listOf(actual))))
                store.write(prayerEditionCacheKey(actual),PrayerDetail.serializer(),value)
                store.write(marker,Int.serializer(),2)
            }
        }else store.write("prayer:$id",PrayerDetail.serializer(),value)
        restore(id,value.languageCode)
        return value
    }
}
