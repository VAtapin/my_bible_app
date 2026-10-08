package com.bibledesktop.myapp.ui.more

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.data.CalendarDownloads
import com.bibledesktop.myapp.data.OfflineStore
import com.bibledesktop.myapp.ui.setup.localized
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

@Composable
internal fun OfflinePanel(language: String) {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context.applicationContext) }
    val jobsFlow = remember(manager) { manager.getWorkInfosForUniqueWorkFlow(CalendarDownloads.name) }
    val jobs by jobsFlow.collectAsState(emptyList())
    val preferences = remember { context.getSharedPreferences(CalendarDownloads.name, android.content.Context.MODE_PRIVATE) }
    val job = jobs.firstOrNull { it.id.toString() == preferences.getString("id", null) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var saved by remember { mutableIntStateOf(0) }
    LaunchedEffect(job?.state, job?.progress) {
        saved = OfflineStore(context).read("pack:${preferences.getString("pack", "")}:${preferences.getString("start", "")}:${preferences.getString("language", "")}", ListSerializer(String.serializer())).orEmpty().size
    }
    var wifi by rememberSaveable { mutableStateOf(preferences.getBoolean("wifi", true)) }
    val active = busy || job?.state == WorkInfo.State.RUNNING || job?.state == WorkInfo.State.ENQUEUED || job?.state == WorkInfo.State.BLOCKED
    val done = maxOf(saved, (if (job?.state == WorkInfo.State.SUCCEEDED) job.outputData else job?.progress)?.getInt("done", 0) ?: 0)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(localized(R.string.offline_title, language), style = MaterialTheme.typography.titleLarge)
            Text(localized(R.string.offline_description, language))
            if (error) Text(localized(R.string.offline_failed, language))
            if (job != null) {
                val start = preferences.getString("start", "").orEmpty()
                val corpus = com.bibledesktop.shared.api.calendarContentLanguage(preferences.getString("language", "ru").orEmpty())
                val corpusTitle = when (corpus) { "de" -> "Deutsch"; "uk" -> "Українська"; else -> "Русский" }
                Text(localized(R.string.offline_package, language, start, corpusTitle))
                Text(localized(when (job.state) {
                    WorkInfo.State.RUNNING -> R.string.offline_running
                    WorkInfo.State.SUCCEEDED -> R.string.offline_complete
                    WorkInfo.State.CANCELLED -> R.string.offline_cancelled
                    WorkInfo.State.FAILED -> R.string.offline_failed
                    else -> R.string.offline_waiting
                }, language))
                LinearProgressIndicator(progress = { done / 30f }, modifier = Modifier.fillMaxWidth())
                Text(localized(R.string.offline_progress, language, done, 30))
            }
            Row {
                Checkbox(wifi, onCheckedChange = { wifi = it }, enabled = !active)
                Text(localized(R.string.offline_wifi, language), Modifier.padding(top = 12.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active) TextButton(onClick = { manager.cancelUniqueWork(CalendarDownloads.name) }) { Text(localized(R.string.offline_cancel, language)) }
                else {
                    Button(onClick = {
                        busy = true; error = false
                        scope.launch {
                            try { CalendarDownloads.enqueue(context, LocalDate.now().toString(), language, wifi) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { error = true }
                            finally { busy = false }
                        }
                    }) { Text(localized(R.string.offline_download, language)) }
                    if (job?.state == WorkInfo.State.CANCELLED || job?.state == WorkInfo.State.FAILED) TextButton(onClick = {
                        busy = true; error = false
                        scope.launch {
                            try { CalendarDownloads.resume(context) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { error = true }
                            finally { busy = false }
                        }
                    }) { Text(localized(R.string.offline_resume, language)) }
                }
            }
        }
    }
}
