package com.bibledesktop.myapp.ui.more

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.TranslationPicker
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*

@Composable
internal fun BibleLibraryScreen(language: String, source: BibleContentSource, onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(BibleDownloads.name, Context.MODE_PRIVATE) }
    val manager = remember { WorkManager.getInstance(context.applicationContext) }
    val flow = remember(manager) { manager.getWorkInfosForUniqueWorkFlow(BibleDownloads.name) }
    val jobs by flow.collectAsState(emptyList())
    val job = jobs.firstOrNull { it.id.toString() == preferences.getString("id", null) }
    val active = job != null && !job.state.isFinished
    var catalog by remember { mutableStateOf<List<TranslationSummary>?>(null) }
    var code by rememberSaveable { mutableStateOf(preferences.getString("code", "").orEmpty()) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var wifi by rememberSaveable { mutableStateOf(preferences.getBoolean("wifi", true)) }
    var pack by remember { mutableStateOf<BiblePackage?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val wifiTitle = localized(R.string.offline_wifi, language)
    LaunchedEffect(retry) {
        error = false
        try {
            catalog = source.getTranslations()
            if (code.isBlank()) code = catalog!!.firstOrNull { it.language.code == language }?.code ?: catalog!!.firstOrNull()?.code.orEmpty()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    LaunchedEffect(code, job?.state, job?.progress, busy) {
        if (active) code = preferences.getString("code", code).orEmpty()
        pack = OfflineStore(context).read(biblePackageKey(code), BiblePackage.serializer())
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().testTag("bible-library")) {
        ReadingHeader(localized(R.string.bible_download_title, language), language, onBack, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localized(R.string.bible_download_hint, language))
            if (catalog == null && !error) CircularProgressIndicator()
            OutlinedButton(enabled = !active && !busy && catalog != null, onClick = { choosing = true }) {
                Text(catalog?.firstOrNull { it.code == code }?.name ?: localized(R.string.bible_choose_translation, language))
            }
            pack?.let {
                LinearProgressIndicator(progress = { it.done.toFloat() / it.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                Text(localized(R.string.bible_download_progress, language, it.done, it.total))
                Text(localized(R.string.bible_download_size, language, it.bytes / (1024f * 1024f)))
                if (it.unavailable.isNotEmpty()) {
                    Text(localized(R.string.bible_download_unavailable, language, it.unavailable.size))
                    Text(it.unavailable.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
                Text(localized(when {
                    active && job.state == WorkInfo.State.RUNNING -> R.string.offline_running
                    active -> R.string.offline_waiting
                    it.complete -> R.string.bible_download_complete
                    it.unavailable.isNotEmpty() && it.done + it.unavailable.size == it.total -> R.string.bible_download_available_done
                    job?.state == WorkInfo.State.FAILED && code == preferences.getString("code", "") -> R.string.bible_download_failed
                    job?.state == WorkInfo.State.CANCELLED && code == preferences.getString("code", "") -> R.string.offline_cancelled
                    else -> R.string.bible_download_partial
                }, language))
            }
            if (pack == null && active) Text(localized(R.string.offline_waiting, language))
            if (pack == null && job?.state == WorkInfo.State.FAILED) Text(localized(R.string.bible_download_failed, language))
            Row {
                Checkbox(wifi, onCheckedChange = { wifi = it }, enabled = !active && !busy,
                    modifier = Modifier.semantics { contentDescription = wifiTitle })
                Text(wifiTitle, Modifier.padding(top = 12.dp))
            }
            if (active) Button(modifier = Modifier.testTag("bible-download-cancel"), onClick = { manager.cancelUniqueWork(BibleDownloads.name) }) { Text(localized(R.string.offline_cancel, language)) }
            else Button(modifier = Modifier.testTag("bible-download-start"), enabled = !busy && code.isNotBlank() && catalog != null, onClick = {
                busy = true; error = false
                scope.launch {
                    try { BibleDownloads.enqueue(context, code, wifi) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }) { Text(localized(if (pack != null && !pack!!.complete) R.string.offline_resume else R.string.bible_download_action, language)) }
            if (error) {
                Text(localized(R.string.bible_download_failed, language))
                TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
            }
        }
    }
    if (choosing) TranslationPicker(language, catalog.orEmpty(), code, onSelect = { code = it; choosing = false }, onClose = { choosing = false })
}
