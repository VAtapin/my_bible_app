package com.bibledesktop.myapp

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import java.util.concurrent.TimeUnit
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class SearchIndexBackgroundTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val directory=File(context.cacheDir,"background-search-${UUID.randomUUID()}")
    private val store=OfflineStore(File(directory,"content"))
    private val file=File(directory,"index.sqlite")
    private val code="INDEX-${UUID.randomUUID()}"
    private val translation=TranslationSummary(code,"Index fixture",language=LanguageSummary("ru","Русский"))
    private val book=BibleBook("genesis","Бытие",chaptersCount=2,canonicalBook=CanonicalBookSummary("Gen","old"))
    @After fun cleanup(){directory.walkBottomUp().forEach{check(it.delete())}}
    private fun chapter(number:Int,text:String)=BibleChapter(translation,book,ChapterSummary(number,1),listOf(BibleVerse(number.toLong(),1,"Gen.$number.1",text,text)))
    private suspend fun installed(text:String="Старый текст"){
        for(number in 1..2)store.write(chapterKey(code,book.slug,number),BibleChapter.serializer(),chapter(number,text))
        store.write(biblePackageKey(code),BiblePackage.serializer(),BiblePackage(translation,listOf(book),done=2,complete=true))
    }
    @Test fun queryBeforePreparationNeverCreatesIndexOrScansCorpus()=runBlocking{
        installed()
        val index=LocalBibleSearch(store,file)
        assertTrue(runCatching{index.search(setOf(code),"текст",VerseSearchMatch.EXACT,VerseSearchScope.ALL)}.exceptionOrNull() is BibleSearchIndexNotReady)
        assertFalse(file.exists())
        assertFalse(index.readiness(setOf(code)))
        assertFalse(file.exists())
    }
    @Test fun interruptedBackgroundPassResumesDurableChapterAndQueriesNeverReadChapterFiles()=runBlocking{
        installed()
        val index=LocalBibleSearch(store,file)
        var done=0
        assertFalse(index.prepare(code,shouldYield={done==1}){value,_->done=value})
        assertFalse(index.readiness(setOf(code)))
        // Resuming reuses its committed staged chapter rather than reading that source again.
        store.write(chapterKey(code,book.slug,1),BibleChapter.serializer(),chapter(1,"Это не staged текст"))
        assertTrue(index.prepare(code))
        assertEquals(2,index.search(setOf(code),"Старый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        // A query reads SQLite and package metadata only, never the installed chapter corpus.
        for(number in 1..2)store.write(chapterKey(code,book.slug,number),BibleChapter.serializer(),chapter(number,"Совсем другой текст"))
        val before=file.lastModified()
        assertEquals(2,index.search(setOf(code),"Старый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        assertEquals(before,file.lastModified())
    }
    @Test fun cancelledAndFailedReplacementKeepPreviousReadyEditionUntilAtomicPublication()=runBlocking{
        installed();val index=LocalBibleSearch(store,file);assertTrue(index.prepare(code))
        delay(5);installed("Новый текст")
        val cancellation=runCatching{index.prepare(code){_,_->throw CancellationException("cancel")}}.exceptionOrNull()
        assertTrue(cancellation is CancellationException)
        assertEquals(SearchIndexReadiness(true,false),index.status(setOf(code)))
        assertEquals(2,index.search(setOf(code),"Старый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        val wrong=chapter(2,"Новый текст").copy(translation=translation.copy(code="OTHER"))
        store.write(chapterKey(code,book.slug,2),BibleChapter.serializer(),wrong)
        assertTrue(runCatching{index.prepare(code)}.isFailure)
        assertEquals(2,index.search(setOf(code),"Старый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        store.write(chapterKey(code,book.slug,2),BibleChapter.serializer(),chapter(2,"Новый текст"))
        assertTrue(index.prepare(code))
        assertEquals(0,index.search(setOf(code),"Старый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        assertEquals(2,index.search(setOf(code),"Новый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        assertEquals(SearchIndexReadiness(true,true),index.status(setOf(code)))
        store.removeBible(code);index.remove(code)
        assertFalse(index.readiness(setOf(code)))
        assertTrue(runCatching{index.search(setOf(code),"Новый",VerseSearchMatch.EXACT,VerseSearchScope.ALL)}.exceptionOrNull() is BibleSearchIndexNotReady)
    }
    @Test fun unfinishedPackageRefreshNeverPublishesMixedIndexAndCorruptReadIsFailClosed()=runBlocking{
        installed();val index=LocalBibleSearch(store,file);assertTrue(index.prepare(code))
        delay(5);installed("Новый текст")
        store.write(bibleRefreshKey(code),BibleRefreshPass.serializer(),BibleRefreshPass("updating",listOf(book),attempted=true))
        assertTrue(index.prepare(code))
        assertEquals(2,index.search(setOf(code),"Старый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        assertEquals(SearchIndexReadiness(true,false),index.status(setOf(code)))
        store.write(bibleRefreshKey(code),BibleRefreshPass.serializer(),BibleRefreshPass("old-finished",listOf(book),finished=true))
        assertFalse(index.prepare(code){done,_->if(done==1)runBlocking{
            store.write(bibleRefreshKey(code),BibleRefreshPass.serializer(),BibleRefreshPass("new-refresh",listOf(book)))
        }})
        assertEquals(2,index.search(setOf(code),"Старый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        val corrupt=File(directory,"corrupt.sqlite").apply{writeText("not a SQLite database")}
        val before=corrupt.readBytes()
        assertEquals(SearchIndexReadiness(false,false),LocalBibleSearch(store,corrupt).status(setOf(code)))
        assertTrue(before.contentEquals(corrupt.readBytes()))
    }
    @Test fun corruptionRepairRequeuesAndRestoresEveryInstalledEditionWithoutStartupGuard()=runBlocking{
        val wrapped=object:ContextWrapper(context){override fun getNoBackupFilesDir()=directory;override fun getApplicationContext()=this}
        val target=OfflineStore(wrapped)
        val otherCode="$code-OTHER"
        val otherTranslation=translation.copy(code=otherCode)
        for((selected,edition) in listOf(code to translation,otherCode to otherTranslation)){
            for(number in 1..2)target.write(chapterKey(selected,book.slug,number),BibleChapter.serializer(),chapter(number,"Общий текст").copy(translation=edition))
            target.write(biblePackageKey(selected),BiblePackage.serializer(),BiblePackage(edition,listOf(book),done=2,complete=true))
            assertTrue(LocalBibleSearch(target,BibleSearchIndexes.file(wrapped)).prepare(selected))
        }
        val database=BibleSearchIndexes.file(wrapped)
        database.writeText("not a SQLite database")
        try{
            // Two real workers compete for one corrupt shared DB. Exactly the first repairs;
            // the following worker must see its fresh replacement, not capture stale corruption.
            val outcomes=coroutineScope {listOf(code,otherCode).map { selected->async(Dispatchers.Default){
                TestListenableWorkerBuilder<BibleSearchIndexWorker>(wrapped).setInputData(workDataOf("code" to selected)).build().doWork()
            }}.awaitAll()}
            assertEquals(1,outcomes.count{it==androidx.work.ListenableWorker.Result.retry()})
            assertEquals(1,outcomes.count{it==androidx.work.ListenableWorker.Result.success()})
            for(selected in listOf(code,otherCode)){
                assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork("native-bible-search-index:$selected").get().isNotEmpty())
                val rebuild=TestListenableWorkerBuilder<BibleSearchIndexWorker>(wrapped).setInputData(workDataOf("code" to selected)).build()
                assertEquals(androidx.work.ListenableWorker.Result.success(),rebuild.doWork())
            }
            val search=LocalBibleSearch(target,database)
            assertEquals(4,search.search(setOf(code,otherCode),"Общий",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
            assertTrue(search.readiness(setOf(code,otherCode)))
        }finally{for(selected in listOf(code,otherCode))WorkManager.getInstance(context).cancelUniqueWork("native-bible-search-index:$selected").result.get()}
    }
    @Test fun completedInstallationQueuesRealBackgroundWorkerAndWorkerPreparesWithoutNetwork()=runBlocking{
        val wrapped=object:ContextWrapper(context){override fun getNoBackupFilesDir()=directory;override fun getApplicationContext()=this}
        val target=OfflineStore(wrapped)
        val api=BibleApiClient()
        val source=object:BibleContentSource by api{
            override suspend fun getTranslations(language:String?)=listOf(translation)
            override suspend fun getBooks(translationCode:String)=listOf(book)
            override suspend fun getChapter(translationCode:String,bookSlug:String,chapterNumber:Int)=chapter(chapterNumber,"Фоновый текст")
        }
        try{
            assertTrue(BibleDownloadEngine(source,target,pause={}).download(code){})
            assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork("native-bible-search-index:$code").get().isNotEmpty())
            val worker=TestListenableWorkerBuilder<BibleSearchIndexWorker>(wrapped).setInputData(workDataOf("code" to code)).build()
            assertEquals(androidx.work.ListenableWorker.Result.success(),worker.doWork())
            assertEquals(2,LocalBibleSearch(target,BibleSearchIndexes.file(wrapped)).search(setOf(code),"Фоновый",VerseSearchMatch.EXACT,VerseSearchScope.ALL).total)
        }finally{WorkManager.getInstance(context).cancelUniqueWork("native-bible-search-index:$code").result.get();api.close()}
    }
    @Test fun installationTriggerIsDurablyAppendedBehindAnUnfinishedIndexJob()=runBlocking {
        val manager=WorkManager.getInstance(context);val unique="native-bible-search-index:$code"
        val old=OneTimeWorkRequestBuilder<BibleSearchIndexWorker>()
            .setInputData(workDataOf("code" to code)).setInitialDelay(1,TimeUnit.DAYS).build()
        try{
            manager.enqueueUniqueWork(unique,ExistingWorkPolicy.KEEP,old).result.get()
            BibleSearchIndexes.enqueue(context,code)
            val queued=manager.getWorkInfosForUniqueWork(unique).get()
            assertEquals(2,queued.size)
            assertEquals(WorkInfo.State.ENQUEUED,queued.single{it.id==old.id}.state)
            assertEquals(WorkInfo.State.BLOCKED,queued.single{it.id!=old.id}.state)
        }finally{manager.cancelUniqueWork(unique).result.get()}
        Unit
    }
}
