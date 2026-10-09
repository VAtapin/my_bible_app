package com.bibledesktop.myapp.ui.more

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.*
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.*

@Composable
internal fun BibleLibraryScreen(language: String, source: BibleContentSource, onBack: () -> Unit,
    onOpen: ((String) -> Unit)? = null) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().testTag("bible-library")) {
        ReadingHeader(localized(R.string.bible_library_title, language), language, onBack, onBack)
        BibleLibraryContent(language, source, Modifier.weight(1f), onOpen = onOpen)
    }
}

/** Shared by setup and the reader: only packages actually saved on this device are installed. */
@Composable
internal fun BibleLibraryContent(language: String, source: BibleContentSource, modifier: Modifier = Modifier,
    onInstalled: (List<TranslationSummary>) -> Unit = {}, onOpen: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { OfflineStore(context) }
    val preferences = remember { context.getSharedPreferences(BibleDownloads.name, Context.MODE_PRIVATE) }
    val manager = remember { WorkManager.getInstance(context.applicationContext) }
    val jobs by remember(manager) { manager.getWorkInfosByTagFlow(BibleDownloads.name) }.collectAsState(emptyList())
    val legacy by remember(manager) { manager.getWorkInfosForUniqueWorkFlow(BibleDownloads.name) }.collectAsState(emptyList())
    val work = (jobs + legacy).distinctBy { it.id }
    var catalog by remember { mutableStateOf<List<TranslationSummary>>(emptyList()) }
    var packages by remember { mutableStateOf<List<BiblePackage>>(emptyList()) }
    var tab by rememberSaveable { mutableStateOf("installed") }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var languageCode by rememberSaveable { mutableStateOf("") }
    var wifi by rememberSaveable { mutableStateOf(preferences.getBoolean("wifi", true)) }
    var expanded by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val installedCallback by rememberUpdatedState(onInstalled)
    LaunchedEffect(work.map { it.id to it.state }, busy) {
        if (source is OfflineContentRepository) source.installedTranslations()
        do {
            packages = store.biblePackages()
            installedCallback(packages.filter { it.isInstalled }.map { it.translation })
            if (work.none { !it.state.isFinished } && busy.isEmpty()) break
            delay(1500)
        } while (true)
    }
    LaunchedEffect(tab, retry) {
        if (tab != "catalog") return@LaunchedEffect
        loading = true; error = false
        try {
            catalog = if (source is OfflineContentRepository) source.refreshTranslations() else source.getTranslations()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
        finally { loading = false }
    }
    val editions = (if (tab == "catalog") catalog else packages.map { it.translation }).distinctBy { it.code }
    val visible = matchingTranslations(editions, query, group, languageCode)
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("installed" to R.string.catalog_installed, "catalog" to R.string.catalog_all).forEach { (id, title) ->
                FilterChip(selected = tab == id, onClick = { tab = id; group = ""; languageCode = ""; query = "" },
                    label = { Text(localized(title, language)) }, modifier = Modifier.weight(1f).testTag("library-$id"))
            }
        }
        if (tab == "installed") Button(onClick = { tab = "catalog"; group = ""; languageCode = ""; query = "" },
            modifier = Modifier.fillMaxWidth().testTag("library-add")) {
            Text(localized(R.string.catalog_add_other, language))
        }
        CatalogFilters(language, editions, query, { query = it }, group, { group = it }, languageCode, { languageCode = it })
        if (tab == "catalog") Row {
            Checkbox(wifi, onCheckedChange = { wifi = it; preferences.edit().putBoolean("wifi", it).apply() })
            Text(localized(R.string.offline_wifi, language), Modifier.padding(top = 12.dp))
        }
        Text(localized(R.string.catalog_results, language, visible.size), style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) Row {
            Text(localized(R.string.load_error, language), Modifier.weight(1f))
            TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
        }
        LazyColumn(Modifier.weight(1f).testTag("library-list"), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (editions.isEmpty() && tab == "installed") item {
                Text(localized(R.string.catalog_empty, language))
            }
            else if (visible.isEmpty() && !loading) item { Text(localized(R.string.translation_not_found, language)) }
            items(visible, key = TranslationSummary::code) { edition ->
                val pack = packages.firstOrNull { it.translation.code == edition.code }
                val candidates = work.filter { "bible-code:${edition.code}" in it.tags ||
                    (it.id.toString() == preferences.getString("id", null) && edition.code == preferences.getString("code", null)) }
                val job = candidates.firstOrNull { !it.state.isFinished }
                    ?: candidates.firstOrNull { it.id.toString() == preferences.getString("id:${edition.code}", null) }
                    ?: candidates.firstOrNull()
                val active = job != null && !job.state.isFinished
                Card(Modifier.fillMaxWidth().testTag("library-${edition.code}")) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(edition.name, style = MaterialTheme.typography.titleSmall)
                        if (edition.code == BundledBible.code && pack?.isInstalled == true) Text(localized(R.string.catalog_bundled, language), style = MaterialTheme.typography.bodySmall)
                        Text("${edition.language.nativeName ?: edition.language.name} · ${edition.shortName ?: edition.code}", style = MaterialTheme.typography.bodySmall)
                        Text(localized(when {
                            edition.hasOldTestament && edition.hasNewTestament -> R.string.catalog_both_testaments
                            edition.hasNewTestament -> R.string.bible_new_testament
                            edition.hasOldTestament -> R.string.bible_old_testament
                            else -> R.string.bible_other_books
                        }, language), style = MaterialTheme.typography.bodySmall)
                        if (pack != null) {
                            Text(localized(R.string.bible_download_progress, language, pack.done, pack.total))
                            if (active || !pack.isInstalled) LinearProgressIndicator(progress = { pack.done.toFloat() / pack.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                            Text(localized(when {
                                active && job.state == WorkInfo.State.RUNNING -> R.string.offline_running
                                active -> R.string.offline_waiting
                                pack.complete -> R.string.bible_download_complete
                                pack.isInstalled -> R.string.bible_download_available_done
                                job?.state == WorkInfo.State.FAILED -> R.string.bible_download_failed
                                job?.state == WorkInfo.State.CANCELLED -> R.string.offline_cancelled
                                else -> R.string.bible_download_partial
                            }, language))
                            if (pack.unavailable.isNotEmpty()) Text(localized(R.string.catalog_missing, language, pack.unavailable.size))
                            if (pack.missingVerses.isNotEmpty()) Text(localized(R.string.catalog_missing_verses, language, pack.missingVerses.size))
                            TextButton(onClick = { expanded = if (expanded == edition.code) "" else edition.code }) { Text(localized(R.string.catalog_details, language)) }
                            if (expanded == edition.code) {
                                Text(localized(R.string.bible_download_size, language, pack.bytes / (1024f * 1024f)))
                                if (pack.unavailable.isNotEmpty()) Text(pack.unavailable.joinToString(" · "))
                                if (pack.missingVerses.isNotEmpty()) Text(pack.missingVerses.joinToString(" · "))
                            }
                        } else if (active) Text(localized(R.string.offline_waiting, language))
                        else if (job?.state == WorkInfo.State.FAILED) Text(localized(R.string.bible_download_failed, language))
                        when {
                            active -> OutlinedButton(onClick = { manager.cancelWorkById(job.id) }, modifier = Modifier.testTag("cancel-${edition.code}")) { Text(localized(R.string.offline_cancel, language)) }
                            pack?.isInstalled == true && onOpen != null -> Button(onClick = { onOpen(edition.code) }, modifier = Modifier.testTag("open-${edition.code}")) { Text(localized(R.string.catalog_read, language)) }
                            pack?.complete == true -> Text(localized(R.string.catalog_installed, language))
                        }
                        if (!active && pack?.complete != true) Button(enabled = busy.isEmpty(), modifier = Modifier.testTag("install-${edition.code}"), onClick = {
                            busy = edition.code; expanded = edition.code
                            scope.launch {
                                try { BibleDownloads.enqueue(context, edition.code, wifi) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { error = true }
                                finally { busy = "" }
                            }
                        }) { Text(localized(if (pack == null) R.string.catalog_install else R.string.offline_resume, language)) }
                    }
                }
            }
        }
    }
}
