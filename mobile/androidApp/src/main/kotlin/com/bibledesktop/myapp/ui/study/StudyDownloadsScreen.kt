package com.bibledesktop.myapp.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.StudyCheck
import com.bibledesktop.myapp.ui.bible.personalStudyText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.bibledesktop.shared.api.DictionaryReference

private val packageText = mapOf(
 "ru" to listOf("Материалы офлайн", "Обновить каталог", "Только Wi-Fi", "Скачать", "Обновить", "Установлено", "Отменить", "Продолжить", "Ожидание сети", "Загрузка / проверка", "Не удалось установить пакет. Повторите загрузку.", "Каталог ещё не опубликован или недоступен. Повторите после сборки пакетов на сервере.", "Закрыть"),
 "de" to listOf("Offline-Materialien", "Katalog aktualisieren", "Nur WLAN", "Herunterladen", "Aktualisieren", "Installiert", "Abbrechen", "Fortsetzen", "Warte auf Netzwerk", "Download / Prüfung", "Paketinstallation fehlgeschlagen. Erneut herunterladen.", "Katalog noch nicht veröffentlicht oder nicht erreichbar. Nach der Paketbereitstellung erneut versuchen.", "Schließen"),
 "uk" to listOf("Матеріали офлайн", "Оновити каталог", "Лише Wi-Fi", "Завантажити", "Оновити", "Встановлено", "Скасувати", "Продовжити", "Очікування мережі", "Завантаження / перевірка", "Не вдалося встановити пакет. Повторіть завантаження.", "Каталог ще не опубліковано або недоступний. Повторіть після створення пакетів на сервері.", "Закрити"),
 "en" to listOf("Offline materials", "Refresh catalog", "Wi-Fi only", "Download", "Update", "Installed", "Cancel", "Resume", "Waiting for network", "Downloading / verifying", "Package installation failed. Try downloading again.", "Catalog is unpublished or unavailable. Retry after packages are built on the server.", "Close")
)
private fun packageActions(language:String)=when(language){"ru"->listOf("Удалить модуль","Удалить скачанный модуль? Личные заметки и закладки сохранятся. Материал можно скачать снова.","Набор","Все","Словари","Толкования","Стронг","Размер загрузки","Скачать выбранное","Очистить выбор");"de"->listOf("Modul löschen","Heruntergeladenes Modul löschen? Persönliche Notizen und Lesezeichen bleiben. Erneutes Herunterladen ist möglich.","Paketgruppe","Alle","Wörterbücher","Kommentare","Strong","Downloadgröße","Auswahl herunterladen","Auswahl leeren");"uk"->listOf("Видалити модуль","Видалити завантажений модуль? Особисті нотатки й закладки залишаться. Матеріал можна завантажити знову.","Набір","Усі","Словники","Тлумачення","Стронг","Розмір завантаження","Завантажити вибране","Очистити вибір");else->listOf("Delete module","Delete the downloaded module? Personal notes and bookmarks stay. You can download it again.","Bundle","All","Dictionaries","Commentaries","Strong","Download size","Download selected","Clear selection")}
internal fun studyDownloadsTitle(language: String) = (packageText[language] ?: packageText.getValue("en"))[0]
@Composable internal fun StudyDownloadsScreen(language: String, onBack: () -> Unit, onReference: ((DictionaryReference)->Unit)? = null) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val text = packageText[language] ?: packageText.getValue("en")
    val store = remember { StudyPackageStore(context) }; val manager = remember { WorkManager.getInstance(context) }
    val jobs by manager.getWorkInfosByTagFlow(StudyPackageDownloads.Tag).collectAsState(initial = emptyList())
    var available by remember { mutableStateOf<List<StudyOfflinePackage>>(emptyList()) }; var installed by remember { mutableStateOf<List<StudyOfflinePackage>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf(false) }; var query by rememberSaveable { mutableStateOf("") }; var wifi by rememberSaveable { mutableStateOf(true) }
    var reading by remember { mutableStateOf<StudyOfflinePackage?>(null) }
    var deleting by remember{mutableStateOf<StudyOfflinePackage?>(null)}
    var selected by rememberSaveable{mutableStateOf(emptyList<String>())}
    val actions=packageActions(language)
    val bundle=available.filter{it.id in selected}
    val toDownload=bundle.filter{pack->installed.none{it.id==pack.id&&it.version==pack.version}}
    fun refresh() { scope.launch { busy = true; error = false; try { installed = store.installed(); available = store.manifest().packages } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { error = true } finally { busy = false } } }
    LaunchedEffect(Unit) { refresh() }; LaunchedEffect(jobs.map { it.state }) { installed = store.installed() }
    BackHandler(onBack = onBack)
    reading?.let { StudyPackageReader(language,it,{reading=null},onReference); return }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(text[0], style = MaterialTheme.typography.titleLarge); TextButton(onClick = onBack) { Text(text[12]) } }
        OutlinedTextField(query, { query = it }, label = { Text(text[0]) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        StudyCheck(text[2], wifi) { wifi = it }; TextButton(onClick = { refresh() }, enabled = !busy) { Text(text[1]) }
        Text(actions[2]+" · "+bundle.size)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){TextButton(onClick={selected=available.map{it.id}}){Text(actions[3])};TextButton(onClick={selected=available.filter{it.kind=="dictionary"}.map{it.id}}){Text(actions[4])};TextButton(onClick={selected=available.filter{it.kind=="commentary"}.map{it.id}}){Text(actions[5])};TextButton(onClick={selected=available.filter{it.kind=="strong"}.map{it.id}}){Text(actions[6])}}
        Text(actions[7]+": "+toDownload.sumOf{it.bytes}/1024+" KB")
        FlowRow{Button(onClick={toDownload.forEach{StudyPackageDownloads.enqueue(context,it,wifi)}},enabled=toDownload.isNotEmpty()){Text(actions[8])};TextButton(onClick={selected=emptyList()}){Text(actions[9])}}
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()); if (error) Text(text[11], color = MaterialTheme.colorScheme.error)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items((available + installed.filter { old -> available.none { it.id == old.id } }).filter { it.id.contains(query, true) }, key = { it.id }) { pack ->
                val saved = installed.firstOrNull { it.id == pack.id }; val job = jobs.filter { StudyPackageDownloads.name(pack.id) in it.tags }.maxByOrNull { if (it.state.isFinished) 0 else 1 }
                val downloading = job?.state in listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if(available.any{it.id==pack.id})StudyCheck(pack.id,pack.id in selected){checked->selected=if(checked)(selected+pack.id).distinct()else selected-pack.id}else Text(pack.id)
                    Text("${pack.bytes / 1024} KB")
                    if (saved != null) { Text(text[5] + " · " + saved.version.take(8)); Row{TextButton(onClick={reading=saved}){Text(personalStudyText(language,"show")+" →")};TextButton(onClick={deleting=saved},enabled=!downloading){Text(actions[0])}} }
                    if (downloading) { val done = job?.progress?.getLong("done", 0) ?: 0; LinearProgressIndicator(progress = { (done.toFloat() / pack.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); Text(if (job?.state == WorkInfo.State.RUNNING) "${text[9]} · ${done / 1024}/${pack.bytes / 1024} KB" else text[8]); TextButton(onClick = { StudyPackageDownloads.cancel(context, pack.id) }) { Text(text[6]) } }
                    else if (saved?.version != pack.version) { if (job?.state == WorkInfo.State.FAILED) Text(text[10], color = MaterialTheme.colorScheme.error); Button(onClick = { StudyPackageDownloads.enqueue(context, pack, wifi) }, enabled = available.any { it.id == pack.id }) { Text(if (saved != null) text[4] else if (job?.state == WorkInfo.State.CANCELLED || job?.state == WorkInfo.State.FAILED) text[7] else text[3]) } }
                } }
            }
        }
    }
    deleting?.let{pack->AlertDialog(onDismissRequest={deleting=null},title={Text(actions[0]+": "+pack.id)},text={Text(actions[1])},confirmButton={TextButton(onClick={scope.launch{try{store.remove(pack.id);installed=store.installed()}catch(_:Exception){error=true};deleting=null}}){Text(actions[0])}},dismissButton={TextButton(onClick={deleting=null}){Text(text[6])}})}
}
