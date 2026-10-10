package com.bibledesktop.myapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.work.*
import com.bibledesktop.shared.api.isInstalled
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Installation/startup owns preparation. Search never queues or builds an index. */
internal object BibleSearchIndexes {
    private const val name="native-bible-search-index"
    private val bootstrapped=AtomicBoolean(false)
    fun file(context:Context)=File(context.noBackupFilesDir,"verse-search-v1.sqlite")
    suspend fun enqueue(context:Context,code:String)=withContext(Dispatchers.IO){
        require(code.isNotBlank()&&code.length<=128)
        WorkManager.getInstance(context).enqueueUniqueWork("$name:$code",ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<BibleSearchIndexWorker>().setInputData(workDataOf("code" to code))
                .addTag(name).addTag("search-code:$code").setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).build()).result.get()
    }
    suspend fun bootstrap(context:Context){
        if(!bootstrapped.compareAndSet(false,true))return
        try{enqueueInstalled(context)}
        catch(error:Exception){bootstrapped.set(false);throw error}
    }
    /** A shared derived DB repair invalidates every edition, independently of startup migration. */
    suspend fun enqueueInstalled(context:Context,store:OfflineStore=OfflineStore(context)){
        for(pack in store.biblePackages())if(pack.isInstalled)enqueue(context,pack.translation.code)
    }
    suspend fun remove(context:Context,store:OfflineStore,code:String)=withContext(Dispatchers.IO){
        WorkManager.getInstance(context).cancelUniqueWork("$name:$code").result.get()
        LocalBibleSearch(store,file(context)).remove(code)
    }
}

class BibleSearchIndexWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result{
        val code=inputData.getString("code")?.takeIf{it.isNotBlank()}?:return Result.failure()
        return try{
            var repaired=false
            val finished=BibleDownloads.downloadLock.withLock {
                val deadline=android.os.SystemClock.elapsedRealtime()+5*60_000
                val index=LocalBibleSearch(OfflineStore(applicationContext),BibleSearchIndexes.file(applicationContext))
                try{index.prepare(code,shouldYield={android.os.SystemClock.elapsedRealtime()>=deadline})}
                catch(_:SQLiteDatabaseCorruptException){
                    // Keep the installation/build lock until deletion: a late corrupt catch must
                    // never discard a fresh shared DB prepared by the next edition's worker.
                    index.discardCorruptIndex();repaired=true;false
                }
            }
            if(repaired)BibleSearchIndexes.enqueueInstalled(applicationContext)
            if(finished)Result.success()else Result.retry()
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){if(runAttemptCount<3)Result.retry()else Result.failure()}
    }
}
