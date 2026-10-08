package com.bibledesktop.myapp.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.work.*
import com.bibledesktop.shared.api.CalendarIcon
import com.bibledesktop.shared.api.isRetryableBibleFailure
import com.bibledesktop.shared.api.bibleRetryDelayMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.time.YearMonth

internal object CalendarDownloads {
    const val name = "native-calendar-offline"
    private val enqueueLock = Mutex()
    suspend fun enqueue(context: Context, start: String, language: String, wifiOnly: Boolean = true, resume: Boolean = false) = withContext(Dispatchers.IO) {
      enqueueLock.withLock {
        require(language in setOf("ru", "de", "uk", "en"))
        val date = LocalDate.parse(start)
        require(date.year in 1900..2100 && date.plusDays(29).year <= 2100)
        val manager = WorkManager.getInstance(context)
        if (manager.getWorkInfosForUniqueWork(name).get().any { !it.state.isFinished }) return@withLock
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val pack = if (resume) preferences.getString("pack", null) ?: return@withLock else java.util.UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<CalendarDownloadWorker>()
            .setInputData(workDataOf("start" to start, "language" to language, "pack" to pack))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
            .build()
        check(preferences.edit().putString("start", start).putString("language", language).putBoolean("wifi", wifiOnly)
            .putString("id", request.id.toString()).putString("pack", pack).commit())
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request).result.get()
      }
    }
    suspend fun resume(context: Context) {
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        enqueue(context, preferences.getString("start", null) ?: return,
            preferences.getString("language", null) ?: return, preferences.getBoolean("wifi", true), resume = true)
    }
}

/** Checkpoints and all resources are durable before a day is counted as complete. */
class CalendarDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val start = inputData.getString("start") ?: return Result.failure()
        val language = inputData.getString("language") ?: return Result.failure()
        val pack = inputData.getString("pack") ?: return Result.failure()
        val repository = OfflineContentRepository(applicationContext)
        var currentDate = start
        try {
            val retryAt = repository.store.read("calendar-api-retry-at", Long.serializer()) ?: 0L
            if (System.currentTimeMillis() < retryAt) return Result.retry()
            val date = LocalDate.parse(start)
            require(date.year in 1900..2100 && date.plusDays(29).year <= 2100 && language in setOf("ru", "de", "uk", "en"))
            val checkpoint = "pack:$pack:$start:$language"
            val completed = repository.store.read(checkpoint, ListSerializer(String.serializer())).orEmpty().toMutableSet()
            setProgress(workDataOf("done" to completed.size, "total" to 30))
            (0..29).map { YearMonth.from(date.plusDays(it.toLong())) }.distinct().forEach {
                delay(2_500) // BibleDesktop's public calendar limit is 30 requests/minute; leave room for interactive reads.
                repository.refreshMonth(it.year, it.monthValue, language)
            }
            for (offset in 0..29) {
                currentCoroutineContext().ensureActive()
                val iso = date.plusDays(offset.toLong()).toString()
                currentDate = iso
                if (iso in completed) continue
                delay(2_500)
                val day = repository.refreshDay(iso, language)
                delay(2_500)
                repository.refreshService(iso, "cu-civil")
                for (icon in day.icons) {
                    currentCoroutineContext().ensureActive()
                    if (icon.localCachingAllowed) saveIconPreview(repository.store, icon)
                }
                completed += iso
                repository.store.write(checkpoint, ListSerializer(String.serializer()), completed.toList())
                setProgress(workDataOf("done" to completed.size, "total" to 30))
            }
            return Result.success(workDataOf("done" to 30, "total" to 30, "start" to start, "language" to language))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IOException) { return if (runAttemptCount < 5) Result.retry() else Result.failure(workDataOf("error" to "network")) }
        catch (error: Exception) {
            if (isRetryableBibleFailure(error) && runAttemptCount < 5) {
                val now = System.currentTimeMillis()
                val wait = bibleRetryDelayMillis(error).coerceAtMost(Long.MAX_VALUE - now)
                repository.store.write("calendar-api-retry-at", Long.serializer(), now + wait)
            }
            return if (isRetryableBibleFailure(error) && runAttemptCount < 5) Result.retry()
                else Result.failure(workDataOf("error" to error.javaClass.simpleName, "date" to currentDate))
        }
        finally { repository.close() }
    }
}

/** Only the first, compact API preview per icon, never an original or third-party URL. */
private suspend fun saveIconPreview(store: OfflineStore, icon: CalendarIcon) {
    val url = CalendarMedia.url(icon.imagePreviewUrl)?.takeIf { URI(it).query == "preview=1" } ?: throw IOException("Preview missing")
    if (store.image(url) != null) return
    val bytes = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 10_000; connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) throw IOException("Preview unavailable")
            require(connection.contentLengthLong <= 384 * 1024)
            connection.inputStream.use { input ->
                val result = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count == -1) break
                    require(result.size() + count <= 384 * 1024)
                    result.write(buffer, 0, count)
                }
                result.toByteArray()
            }
        } finally { connection.disconnect() }
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth in 1..1024 && options.outHeight in 1..1024)
    store.saveImage(url, bytes)
}
