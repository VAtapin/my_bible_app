package com.bibledesktop.myapp.data

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

internal const val bundledAtlasId="atlas:openbible-v1"
@Serializable internal data class OfflineBundle(val id:String,val name:String,val items:List<String>)
@Serializable internal data class OfflineBundleRecords(val schema:Int,val bundles:List<OfflineBundle>)
internal enum class BundleKind { BIBLE,STUDY,ATLAS }
internal data class BundleItemId(val kind:BundleKind,val code:String) {
    val value get()=when(kind){BundleKind.BIBLE->"bible:";BundleKind.STUDY->"study:";BundleKind.ATLAS->"atlas:"}+code
    companion object { fun parse(value:String):BundleItemId {
        require(Regex("(?:(?:bible|study):[A-Za-z0-9][A-Za-z0-9_.-]*|atlas:openbible-v1)").matches(value))
        val kind=when(value.substringBefore(':')){"bible"->BundleKind.BIBLE;"study"->BundleKind.STUDY;"atlas"->BundleKind.ATLAS;else->error("Invalid bundle member")}
        val code=value.substringAfter(':',"")
        require(kind!=BundleKind.ATLAS||value==bundledAtlasId)
        return BundleItemId(kind,code)
    } }
}
internal enum class BundleMemberState { READY,MISSING,UPDATABLE,RUNNING,WAITING,CANCELLED,FAILED,SOURCE_UNAVAILABLE }
internal data class BundleMember(val id:String,val name:String,val state:BundleMemberState,val bytes:Long?,val detail:String="",val estimated:Boolean=false)
internal data class BundleSnapshot(val members:List<BundleMember>) {
    val ready get()=members.count{it.state==BundleMemberState.READY}
    val complete get()=members.isNotEmpty()&&ready==members.size
    val knownBytes get()=members.filter{it.state!=BundleMemberState.READY}.mapNotNull{it.bytes}.sum()
    val estimated get()=members.any{it.state!=BundleMemberState.READY&&it.estimated}
    val unknownSizes get()=members.count{it.state!=BundleMemberState.READY&&it.bytes==null}
}

/** Composition only: content and jobs remain in the existing Bible/ZIP stores and loaders. */
internal class OfflineBundleCoordinator(private val context:Context,private val cache:OfflineStore=OfflineStore(context),private val study:StudyPackageStore=StudyPackageStore(context)) {
    private val bundlesKey="offline-bundles-v1"
    private val audits=mutableMapOf<String,Pair<Long,Boolean>>()
    fun clearAudits(){audits.clear()}
    private suspend fun bibleReady(pack:BiblePackage?):Boolean {
        if(pack?.complete!=true||cache.read(bibleRefreshKey(pack.translation.code),BibleRefreshPass.serializer())?.finished==false)return false
        val stamp=cache.savedAt(biblePackageKey(pack.translation.code));audits[pack.translation.code]?.takeIf{it.first==stamp}?.let{return it.second}
        var valid=pack.total>0&&pack.done==pack.total&&pack.total==pack.books.sumOf{it.chaptersCount}&&pack.unavailable.isEmpty()&&pack.missingVerses.isEmpty()
        if(valid)for(book in pack.books)for(number in 1..book.chaptersCount){
            val chapter=cache.read(chapterKey(pack.translation.code,book.slug,number),BibleChapter.serializer())
            if(chapter==null||chapter.translation.code!=pack.translation.code||chapter.book.slug!=book.slug||chapter.chapter.number!=number||chapter.verses.isEmpty()||chapter.verses.size!=chapter.chapter.versesCount||chapter.verses.any{it.plainText.isBlank()||it.osisRef.isBlank()}||chapter.verses.map{it.osisRef}.distinct().size!=chapter.verses.size)valid=false
        }
        audits[pack.translation.code]=stamp to valid;return valid
    }
    private fun revised(saved:BiblePackage?,available:TranslationSummary?)=saved?.translation?.contentRevision!=null&&available?.contentRevision!=null&&saved.translation.contentRevision!=available.contentRevision
    private fun validateItems(items:List<String>,allowEmpty:Boolean=false):List<BundleItemId> {
        require(items.size<=100&&(allowEmpty||items.isNotEmpty())&&items.distinct().size==items.size)
        return items.map{BundleItemId.parse(it)}
    }
    private fun valid(bundle:OfflineBundle)=runCatching{
        require(Regex("[A-Za-z0-9_-]{1,128}").matches(bundle.id)&&bundle.name.isNotBlank()&&bundle.name.length<=80)
        validateItems(bundle.items)
    }.isSuccess
    suspend fun bundles():List<OfflineBundle> {
        val stored=cache.read(bundlesKey,JsonElement.serializer()) ?: return emptyList()
        val values=when(stored){is JsonArray->stored;is JsonObject->if(stored["schema"]==JsonPrimitive(1))stored["bundles"] as? JsonArray else null;else->null} ?: return emptyList()
        val json=Json{ignoreUnknownKeys=true}
        val records=values.mapNotNull{value->
            val record=value as? JsonObject ?: return@mapNotNull null
            if((record["id"] as? JsonPrimitive)?.isString!=true||(record["name"] as? JsonPrimitive)?.isString!=true)return@mapNotNull null
            val items=record["items"] as? JsonArray ?: return@mapNotNull null
            if(items.any{(it as? JsonPrimitive)?.isString!=true})return@mapNotNull null
            runCatching{json.decodeFromJsonElement(OfflineBundle.serializer(),record)}.getOrNull()?.takeIf(::valid)
        }.distinctBy{it.id}
        if(stored is JsonArray)cache.write(bundlesKey,OfflineBundleRecords.serializer(),OfflineBundleRecords(schema=1,bundles=records))
        return records
    }
    suspend fun save(name:String,items:List<String>,id:String=UUID.randomUUID().toString()):OfflineBundle {
        require(name.length<=80)
        val bundle=OfflineBundle(id,name.trim(),items)
        require(valid(bundle))
        cache.write(bundlesKey,OfflineBundleRecords.serializer(),OfflineBundleRecords(schema=1,bundles=bundles().filterNot{it.id==id}+bundle))
        return bundle
    }
    suspend fun forget(id:String)=cache.write(bundlesKey,OfflineBundleRecords.serializer(),OfflineBundleRecords(schema=1,bundles=bundles().filterNot{it.id==id}))
    suspend fun snapshot(items:List<String>,bibles:List<TranslationSummary>,published:List<StudyOfflinePackage>,jobs:List<WorkInfo>):BundleSnapshot {
        validateItems(items,allowEmpty=true)
        val installedBibles=cache.biblePackages();val installedStudy=study.installed()
        return BundleSnapshot(items.distinct().map { value ->
            val item=BundleItemId.parse(value)
            val tag=when(item.kind){BundleKind.BIBLE->"bible-code:${item.code}";BundleKind.STUDY->StudyPackageDownloads.name(item.code);BundleKind.ATLAS->""}
            val preferred=if(item.kind==BundleKind.BIBLE)context.getSharedPreferences(BibleDownloads.name,Context.MODE_PRIVATE).getString("id:${item.code}",null)else null
            val job=jobs.firstOrNull{it.id.toString()==preferred}?:jobs.filter{tag in it.tags}.maxWithOrNull(compareBy<WorkInfo>{if(it.state.isFinished)0 else 1}.thenBy{it.tags.firstOrNull{tag->tag.startsWith("enqueued-at:")}?.substringAfter(':')?.toLongOrNull()?:0})
            val progressDetail=job?.progress?.let{progress->val total=progress.getLong("total",0);if(total>0)"${progress.getLong("done",0)}/${total} B"else""}.orEmpty()
            val active=when(job?.state){WorkInfo.State.RUNNING->BundleMemberState.RUNNING;WorkInfo.State.ENQUEUED,WorkInfo.State.BLOCKED->BundleMemberState.WAITING;else->null}
            when(item.kind){
                BundleKind.ATLAS->BundleMember(value,"OpenBible",BundleMemberState.READY,0)
                BundleKind.BIBLE->{val available=bibles.find{it.code==item.code};val saved=installedBibles.find{it.translation.code==item.code};val unavailable=saved?.unavailable.orEmpty()+saved?.missingVerses.orEmpty()
                    val state=active?:if(job?.state==WorkInfo.State.FAILED&&"bible-refresh" in job.tags)BundleMemberState.FAILED else if(job?.state==WorkInfo.State.CANCELLED&&"bible-refresh" in job.tags)BundleMemberState.CANCELLED else if(bibleReady(saved))if(revised(saved,available))BundleMemberState.UPDATABLE else BundleMemberState.READY else if(available==null||unavailable.isNotEmpty()||job?.outputData?.getString("error")=="source-empty")BundleMemberState.SOURCE_UNAVAILABLE else when(job?.state){WorkInfo.State.CANCELLED->BundleMemberState.CANCELLED;WorkInfo.State.FAILED->BundleMemberState.FAILED;else->BundleMemberState.MISSING}
                    BundleMember(value,available?.name?:saved?.translation?.name?:item.code,state,available?.offlineSizeEstimateBytes,saved?.let{"${it.done}/${it.total}"}.orEmpty(),estimated=available?.offlineSizeEstimateBytes!=null)
                }
                BundleKind.STUDY->{val available=published.find{it.id==item.code};val saved=installedStudy.find{it.id==item.code};val state=active?:if(saved!=null&&available?.version==saved.version)BundleMemberState.READY else if(available==null)BundleMemberState.SOURCE_UNAVAILABLE else when(job?.state){WorkInfo.State.CANCELLED->BundleMemberState.CANCELLED;WorkInfo.State.FAILED->BundleMemberState.FAILED;else->if(saved!=null)BundleMemberState.UPDATABLE else BundleMemberState.MISSING};BundleMember(value,item.code,state,available?.bytes,progressDetail.ifBlank{saved?.version?.take(8).orEmpty()})}
            }
        })
    }
    suspend fun start(items:List<String>,bibles:List<TranslationSummary>,published:List<StudyOfflinePackage>,wifi:Boolean,update:Boolean=false) {
        validateItems(items)
        for(value in items.distinct()){val item=BundleItemId.parse(value);when(item.kind){
            BundleKind.ATLAS->Unit
            BundleKind.BIBLE->{if(bibles.any{it.code==item.code}){val installed=cache.read(biblePackageKey(item.code),BiblePackage.serializer());val changed=revised(installed,bibles.find{it.code==item.code});if(update||changed||!bibleReady(installed))BibleDownloads.enqueue(context,item.code,wifi,refresh=update||changed)}}
            BundleKind.STUDY->{published.find{it.id==item.code}?.let{pack->if(study.installed().none{it.id==pack.id&&it.version==pack.version})StudyPackageDownloads.enqueue(context,pack,wifi)}}
        }}
    }
    suspend fun cancel(items:List<String>)=withContext(Dispatchers.IO){val parsed=validateItems(items);val manager=WorkManager.getInstance(context);parsed.forEach{item->when(item.kind){BundleKind.BIBLE->manager.cancelUniqueWork("${BibleDownloads.name}:${item.code}").result.get();BundleKind.STUDY->StudyPackageDownloads.cancel(context,item.code).result.get();BundleKind.ATLAS->Unit}}}
    suspend fun deleteContent(items:List<String>) {
        cancel(items)
        for(value in items.distinct()){val item=BundleItemId.parse(value);when(item.kind){BundleKind.ATLAS->Unit;BundleKind.BIBLE->BibleDownloads.downloadLock.withLock{cache.removeBible(item.code)};BundleKind.STUDY->study.remove(item.code)}}
    }
}
