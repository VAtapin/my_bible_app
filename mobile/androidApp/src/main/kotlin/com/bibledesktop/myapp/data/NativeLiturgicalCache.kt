package com.bibledesktop.myapp.data

import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Serializable internal data class SavedLiturgicalVersion(val revision:String?,val data:LiturgicalWorkVersion)

/** Exact edition records; access and integrity failures never revive a saved body. */
internal class NativeLiturgicalCache(private val remote:BibleContentSource,private val store:OfflineStore,private val online:()->Boolean){
    private suspend fun <T> read(key:String,serializer:KSerializer<T>,validate:(T)->Unit,fetch:suspend()->T):T{
        val blocked=store.read("liturgical-denied:$key",Boolean.serializer())==true
        val saved=store.read(key,serializer)?.takeIf { runCatching { validate(it) }.isSuccess }
        if(!online()&&blocked)error("Unavailable liturgical request")
        if(!online()&&saved!=null)return saved
        val fresh=try{fetch().also(validate)}catch(cancelled:CancellationException){throw cancelled}catch(error:Exception){
            if(isPrayerUnavailableFailure(error))store.write("liturgical-denied:$key",Boolean.serializer(),true)
            if(!blocked&&saved!=null&&isPrayerTransportFailure(error))return saved
            throw error
        }
        store.write(key,serializer,fresh)
        store.write("liturgical-denied:$key",Boolean.serializer(),false)
        return fresh
    }
    suspend fun works(collection:String)=read("liturgical-list:$collection",ListSerializer(LiturgicalWorkSummary.serializer()),{list->list.forEach{it.validateLiturgicalWork();require(collection in it.collections)}}){remote.getLiturgicalWorks(collection)}
    suspend fun work(slug:String)=read("liturgical-work:$slug",LiturgicalWorkSummary.serializer(),{it.validateLiturgicalWork();require(it.slug==slug||slug in it.legacySlugs)}){remote.getLiturgicalWork(slug)}
    suspend fun version(slug:String,language:String,edition:String?):LiturgicalWorkVersion{
        val summary=work(slug)
        require(language in summary.availableLanguages)
        if(edition!=null)require(summary.editions.any{it.code==edition&&it.language==language})
        return read("liturgical-version:$slug:$language:${edition.orEmpty()}",SavedLiturgicalVersion.serializer(),{saved->
            require(saved.revision==summary.contentRevision)
            require(saved.data.slug==summary.slug)
            saved.data.validateLiturgicalVersion(language,edition,summary.collections.contains("prayers"))
        }){SavedLiturgicalVersion(summary.contentRevision,remote.getLiturgicalVersion(summary.slug,language,edition))}.data
    }
}
