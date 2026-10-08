package com.bibledesktop.myapp.ui.reminders

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import kotlinx.coroutines.*
import java.time.LocalTime
import java.util.Locale

@Composable
internal fun RemindersScreen(language: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var encoded by rememberSaveable { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var allowed by remember { mutableStateOf(ReminderScheduler.allowed(context)) }
    var retry by remember { mutableIntStateOf(0) }
    val draft = encoded?.split('|')?.map { part ->
        val fields = part.split(','); DailyReminder(fields[0], fields[1] == "1", fields[2])
    }
    fun update(values: List<DailyReminder>) {
        encoded = values.joinToString("|") { "${it.id},${if (it.enabled) 1 else 0},${it.time}" }
        saved = false; error = false
    }
    LaunchedEffect(retry) {
        loadError = false
        if (encoded == null) try { update(withContext(Dispatchers.IO) { ReminderStore.read(context) }) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { loadError = true }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = ReminderScheduler.allowed(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun save() {
        val values = draft ?: return
        busy = true; error = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) { ReminderStore.save(context, values); ReminderScheduler.reconcile(context) }
                allowed = ReminderScheduler.allowed(context); saved = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { save() }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        ReadingHeader(localized(R.string.section_reminders, language), language, onBack, onBack)
        LazyColumn(Modifier.weight(1f).testTag("reminders-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(localized(R.string.reminders_hint, language), color = PrimaryBlue) }
            if (loadError) item {
                Text(localized(R.string.reminders_error, language))
                TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
            }
            if (draft == null && !loadError) item { CircularProgressIndicator() }
            items(draft.orEmpty(), key = { it.id }) { entry ->
                val title = localized(reminderTitle(entry.id), language)
                Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            TextButton(enabled = !busy, modifier = Modifier.testTag("reminder-time-${entry.id}"), onClick = {
                                val time = LocalTime.parse(entry.time)
                                TimePickerDialog(context, { _, hour, minute ->
                                    update(draft.orEmpty().map { if (it.id == entry.id) it.copy(time = String.format(Locale.ROOT, "%02d:%02d", hour, minute)) else it })
                                }, time.hour, time.minute, true).show()
                            }) { Text(entry.time) }
                        }
                        Switch(checked = entry.enabled, enabled = !busy, modifier = Modifier.testTag("reminder-toggle-${entry.id}").semantics { contentDescription = title },
                            onCheckedChange = { enabled -> update(draft.orEmpty().map { if (it.id == entry.id) it.copy(enabled = enabled) else it }) })
                    }
                }
            }
            if (!allowed) item {
                Text(localized(R.string.reminders_blocked, language), color = PrimaryBlue)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) {
                    Text(localized(R.string.reminders_system_settings, language))
                }
            }
            if (saved) item { Text(localized(if (allowed || draft?.none { it.enabled } == true) R.string.reminders_saved else R.string.reminders_saved_blocked, language)) }
            if (error) item { Text(localized(R.string.reminders_error, language)) }
        }
        Button(enabled = draft != null && !busy, modifier = Modifier.fillMaxWidth().padding(20.dp), onClick = {
            if (draft?.any { it.enabled } == true && !allowed && Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else save()
        }) { Text(localized(R.string.action_save, language)) }
    }
}
