package com.bibledesktop.myapp.ui.study

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Visible verse requests share a small queue; failed attempts release their slot while waiting. */
internal class InlineReferenceLoader(
    maxConcurrent:Int=2,
    private val startPauseMs:Long=500,
    private val retryPauses:List<Long> = listOf(3_000,9_000,30_000,60_000),
){
    private val slots=Semaphore(maxConcurrent)
    private val starts=Mutex()
    private var lastStart=0L
    init { require(maxConcurrent>0&&startPauseMs>=0&&retryPauses.isNotEmpty()&&retryPauses.all{it>0}) }
    suspend fun <T> load(attempt:suspend ()->T, waitForRetry:suspend (Long)->Unit={delay(it)}):T {
        var failed=0
        while(true){
            try {
                return slots.withPermit {
                    starts.withLock {
                        val remaining=lastStart+startPauseMs-android.os.SystemClock.elapsedRealtime()
                        if(remaining>0)delay(remaining)
                        lastStart=android.os.SystemClock.elapsedRealtime()
                    }
                    attempt()
                }
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){
                waitForRetry(retryPauses[failed.coerceAtMost(retryPauses.lastIndex)])
                if(failed<retryPauses.lastIndex)failed++
            }
        }
    }
}
private val sharedInlineReferences=InlineReferenceLoader()
internal val LocalInlineReferenceLoader=staticCompositionLocalOf{sharedInlineReferences}
