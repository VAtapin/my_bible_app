package com.bibledesktop.myapp.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class StudyPackageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val raw = inputData.getString("package") ?: return Result.failure()
        return try {
            val pack = Json.decodeFromString<StudyOfflinePackage>(raw)
            val store = StudyPackageStore(applicationContext)
            val resumed=store.partialBytes(pack).coerceIn(0,pack.bytes)
            setForeground(studyPackageForeground(applicationContext,id,pack,resumed,pack.bytes,false))
            setProgress(workDataOf("id" to pack.id, "done" to resumed, "total" to pack.bytes, "phase" to "download"))
            var lastPublished=android.os.SystemClock.elapsedRealtime()
            // Yield before Android's six-hour dataSync budget. Verified marker remains untouched until complete.
            withTimeout(5*60*60*1000L){store.download(pack) { done, total ->
                val now=android.os.SystemClock.elapsedRealtime()
                if(now-lastPublished>=1000||done==total){lastPublished=now
                    setProgress(workDataOf("id" to pack.id, "done" to done, "total" to total, "phase" to if (done == total) "verify" else "download"))
                    setForeground(studyPackageForeground(applicationContext,id,pack,done,total,done==total))
                }
            }}
            Result.success(workDataOf("id" to pack.id, "done" to pack.bytes, "total" to pack.bytes))
        } catch (_: TimeoutCancellationException) { Result.retry()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: StudyPackageHttpError) { if (error.status >= 500 && runAttemptCount<5) Result.retry() else Result.failure(workDataOf("error" to "HTTP ${error.status}")) }
        catch (error: IOException) { if (runAttemptCount < 5) Result.retry() else Result.failure(workDataOf("error" to "network")) }
        catch (_: Exception) { Result.failure(workDataOf("error" to "invalid-package")) }
    }
}
internal object StudyPackageDownloads {
    const val Tag = "study-full-packages-v1"
    fun name(code: String) = "$Tag:$code"
    fun enqueue(context: Context, pack: StudyOfflinePackage, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<StudyPackageWorker>().setInputData(workDataOf("package" to Json.encodeToString(pack)))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag(Tag).addTag(name(pack.id)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(pack.id), ExistingWorkPolicy.KEEP, request)
    }
    fun cancel(context: Context, code: String) = WorkManager.getInstance(context).cancelUniqueWork(name(code))
}
