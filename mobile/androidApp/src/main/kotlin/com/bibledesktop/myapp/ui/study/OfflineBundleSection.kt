package com.bibledesktop.myapp.ui.study

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.work.WorkInfo
import com.bibledesktop.myapp.data.*
import com.bibledesktop.myapp.ui.bible.StudyCheck
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

internal fun offlineBundleText(language:String,key:String):String {
    val keys=listOf("title","choose","name","save","atlas","strong","bible","start","resume","retry","update","cancel","delete","confirm","cancelled","failed","waiting","running","ready","missing","updatable","unavailable","unknown","forget","catalog","back","members","estimate","strongEmpty")
    val labels=when(language){
        "ru"->listOf("Офлайн-комплекты","Выбрать состав","Название комплекта","Сохранить комплект","Библия и атлас","Библия и Strong","Библия","Скачать комплект","Продолжить","Повторить","Обновить комплект","Отменить загрузку","Удалить скачанное","Удалить скачанные Библии и материалы комплекта? Личные заметки и закладки сохранятся. Встроенный атлас останется. Материалы можно скачать снова. Эти же источники будут удалены и из других комплектов, которые их используют.","Отменено","Ошибка загрузки","Ожидание сети","Загрузка / проверка","Готово офлайн","Не скачано","Есть обновление","Источник недоступен","Размер Библии не опубликован","Удалить название комплекта","Обновить каталог Библий","Назад","Состав","Приблизительный объём загрузки","Нет опубликованных пакетов Strong. Обновите каталог после публикации пакетов.")
        "de"->listOf("Offline-Pakete","Inhalt auswählen","Paketname","Paket speichern","Bibel und Atlas","Bibel und Strong","Bibel","Paket herunterladen","Fortsetzen","Erneut versuchen","Paket aktualisieren","Download abbrechen","Downloads löschen","Die heruntergeladenen Bibeln und Materialien dieses Pakets löschen? Persönliche Notizen und Lesezeichen bleiben erhalten. Der integrierte Atlas bleibt. Erneutes Herunterladen ist möglich. Dieselben Quellen werden auch aus anderen Paketen entfernt, die sie verwenden.","Abgebrochen","Download fehlgeschlagen","Warte auf Netzwerk","Download / Prüfung","Offline bereit","Nicht heruntergeladen","Aktualisierung verfügbar","Quelle nicht verfügbar","Bibelgröße nicht veröffentlicht","Paketnamen löschen","Bibelkatalog aktualisieren","Zurück","Inhalt","Geschätzte Downloadgröße","Keine veröffentlichten Strong-Pakete. Katalog nach der Paketveröffentlichung aktualisieren.")
        "uk"->listOf("Офлайн-комплекти","Вибрати склад","Назва комплекту","Зберегти комплект","Біблія й атлас","Біблія й Strong","Біблія","Завантажити комплект","Продовжити","Повторити","Оновити комплект","Скасувати завантаження","Видалити завантажене","Видалити завантажені Біблії й матеріали комплекту? Особисті нотатки й закладки залишаться. Вбудований атлас залишиться. Матеріали можна завантажити знову. Ці самі джерела буде видалено й з інших комплектів, які їх використовують.","Скасовано","Помилка завантаження","Очікування мережі","Завантаження / перевірка","Готово офлайн","Не завантажено","Є оновлення","Джерело недоступне","Розмір Біблії не опубліковано","Видалити назву комплекту","Оновити каталог Біблій","Назад","Склад","Приблизний обсяг завантаження","Немає опублікованих пакетів Strong. Оновіть каталог після публікації пакетів.")
        else->listOf("Offline bundles","Choose contents","Bundle name","Save bundle","Bible and atlas","Bible and Strong","Bible","Download bundle","Resume","Retry","Update bundle","Cancel download","Delete downloads","Delete this bundle's downloaded Bibles and materials? Personal notes and bookmarks stay. The built-in atlas stays. You can download the materials again. The same sources will also be removed from other bundles that use them.","Cancelled","Download failed","Waiting for network","Downloading / verifying","Ready offline","Not downloaded","Update available","Source unavailable","Bible size unpublished","Delete bundle name","Refresh Bible catalog","Back","Contents","Estimated download size","No published Strong packages. Refresh the catalog after packages are published.")
    }
    return labels[keys.indexOf(key).also{require(it>=0)}]
}
internal fun bundleStateKey(state:BundleMemberState)=when(state){BundleMemberState.READY->"ready";BundleMemberState.MISSING->"missing";BundleMemberState.UPDATABLE->"updatable";BundleMemberState.RUNNING->"running";BundleMemberState.WAITING->"waiting";BundleMemberState.CANCELLED->"cancelled";BundleMemberState.FAILED->"failed";BundleMemberState.SOURCE_UNAVAILABLE->"unavailable"}

@Composable internal fun OfflineBundleSection(language:String,published:List<StudyOfflinePackage>,jobs:List<WorkInfo>,wifi:Boolean,contentSource:BibleContentSource?=null,contentStore:OfflineStore?=null,bundleCoordinator:OfflineBundleCoordinator?=null) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val cache=remember(contentStore){contentStore?:OfflineStore(context)};val coordinator=remember(bundleCoordinator){bundleCoordinator?:OfflineBundleCoordinator(context)}
    val client=remember(contentSource){contentSource?:BibleApiClient()};DisposableEffect(client){onDispose{if(contentSource==null)(client as?BibleApiClient)?.close()}}
    var catalog by remember{mutableStateOf(emptyList<TranslationSummary>())};var bundles by remember{mutableStateOf(emptyList<OfflineBundle>())}
    var selected by rememberSaveable{mutableStateOf(emptyList<String>())};var name by rememberSaveable{mutableStateOf("")};var bundleId by rememberSaveable{mutableStateOf<String?>(null)}
    var snapshot by remember{mutableStateOf(BundleSnapshot(emptyList()))};var choosing by rememberSaveable{mutableStateOf(false)};var preset by rememberSaveable{mutableStateOf<String?>(null)}
    var selectionQuery by rememberSaveable{mutableStateOf("")}
    var bibleChoice by rememberSaveable{mutableStateOf("")};var strongChoice by rememberSaveable{mutableStateOf("")};var deleting by rememberSaveable{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)};var failed by remember{mutableStateOf(false)};var revision by remember{mutableIntStateOf(0)}
    fun t(key:String)=offlineBundleText(language,key)
    fun work(action:suspend()->Unit){scope.launch{busy=true;failed=false;try{action();bundles=coordinator.bundles();revision++}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failed=true}finally{busy=false}}}
    fun refresh(){work{
        coordinator.clearAudits()
        val cached=cache.read("translations:available:",ListSerializer(TranslationSummary.serializer())).orEmpty()
        catalog=(cached+cache.biblePackages().map{it.translation}).distinctBy{it.code}
        val actual=client.getTranslations();cache.write("translations:available:",ListSerializer(TranslationSummary.serializer()),actual);catalog=actual
    }}
    LaunchedEffect(Unit){bundles=coordinator.bundles();refresh()}
    LaunchedEffect(selected,catalog,published,jobs,revision){snapshot=coordinator.snapshot(selected,catalog,published,jobs)}
    Card(Modifier.fillMaxWidth().testTag("offline-unified-bundles")){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(t("title"),style=MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){TextButton(onClick={choosing=true}){Text(t("choose"))};TextButton(onClick={preset="atlas"}){Text(t("atlas"))};TextButton(onClick={preset="strong"}){Text(t("strong"))}}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){bundles.forEach{bundle->FilterChip(selected=bundleId==bundle.id,onClick={selected=bundle.items;name=bundle.name;bundleId=bundle.id},label={Text(bundle.name)})}}
        OutlinedTextField(name,{if(it.length<=80)name=it},label={Text(t("name"))},modifier=Modifier.fillMaxWidth())
        FlowRow{TextButton(onClick={work{bundleId=coordinator.save(name,selected,bundleId?:java.util.UUID.randomUUID().toString()).id}},enabled=name.isNotBlank()&&selected.isNotEmpty()&&!busy){Text(t("save"))};bundleId?.let{id->TextButton(onClick={work{coordinator.forget(id);bundleId=null}},enabled=!busy){Text(t("forget"))}}}
        OfflineBundleSummary(language,snapshot)
        if(snapshot.members.isNotEmpty())LinearProgressIndicator(progress={snapshot.ready.toFloat()/snapshot.members.size},modifier=Modifier.fillMaxWidth())
        val active=snapshot.members.any{it.state==BundleMemberState.RUNNING||it.state==BundleMemberState.WAITING}
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            Button(onClick={work{coordinator.start(selected,catalog,published,wifi)}},enabled=selected.isNotEmpty()&&!snapshot.complete&&!busy&&!active){Text(t(if(snapshot.members.any{it.state==BundleMemberState.FAILED})"retry"else if(snapshot.members.any{it.state==BundleMemberState.CANCELLED})"resume"else"start"))}
            TextButton(onClick={work{coordinator.cancel(selected)}},enabled=active&&!busy){Text(t("cancel"))}
            TextButton(onClick={work{coordinator.start(selected,catalog,published,wifi,update=true)}},enabled=selected.any{it.startsWith("bible:")||it.startsWith("study:")}&&!active&&!busy){Text(t("update"))}
            TextButton(onClick={deleting=true},enabled=selected.any{it.startsWith("bible:")||it.startsWith("study:")}&&!busy){Text(t("delete"))}
        }
        TextButton(onClick=::refresh,enabled=!busy){Text(t("catalog"))}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());if(failed)Text(t("unavailable"),color=MaterialTheme.colorScheme.error)
    }}
    if(choosing)Dialog(onDismissRequest={choosing=false},properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){Column(Modifier.padding(16.dp)){
        TextButton(onClick={choosing=false}){Text(t("back"))};OutlinedTextField(selectionQuery,{selectionQuery=it},label={Text(t("choose"))},modifier=Modifier.fillMaxWidth());LazyColumn{item{StudyCheck("OpenBible · "+t("ready"),bundledAtlasId in selected){checked->selected=if(checked)(selected+bundledAtlasId).distinct()else selected-bundledAtlasId}}
            items(catalog.filter{(it.name+" "+it.code+" "+it.language.name).contains(selectionQuery,true)},key={"bible:"+it.code}){bible->val id="bible:"+bible.code;StudyCheck(t("bible")+" · "+bible.name,id in selected){checked->selected=if(checked)(selected+id).distinct()else selected-id;bundleId=null}}
            items(published.filter{(it.id+" "+it.kind).contains(selectionQuery,true)},key={"study:"+it.id}){pack->val id="study:"+pack.id;StudyCheck(pack.id+" · ${pack.bytes/1024} KB",id in selected){checked->selected=if(checked)(selected+id).distinct()else selected-id;bundleId=null}}
        }
    }}}
    preset?.let{kind->Dialog(onDismissRequest={preset=null},properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){Column(Modifier.padding(16.dp)){
        Text(t(kind),style=MaterialTheme.typography.titleLarge);TextButton(onClick={preset=null}){Text(t("back"))}
        LazyColumn(Modifier.weight(1f)){items(catalog,key={it.code}){bible->RadioButtonRow(t("bible")+" · "+bible.name,bibleChoice==bible.code){bibleChoice=bible.code}}
            if(kind=="strong"&&published.none{it.kind=="strong"})item{Text(t("strongEmpty"))}
            if(kind=="strong")items(published.filter{it.kind=="strong"},key={it.id}){pack->RadioButtonRow(pack.id,strongChoice==pack.id){strongChoice=pack.id}}
        }
        Button(onClick={selected=listOf("bible:$bibleChoice",if(kind=="atlas")bundledAtlasId else"study:$strongChoice");name=t(kind);bundleId=null;preset=null},enabled=catalog.any{it.code==bibleChoice}&&(kind=="atlas"||published.any{it.kind=="strong"&&it.id==strongChoice})){Text(t("choose"))}
    }}}}
    if(deleting)AlertDialog(
        onDismissRequest={deleting=false},title={Text(t("delete"))},text={Text(t("confirm"))},
        confirmButton={TextButton(onClick={deleting=false;work{coordinator.deleteContent(selected)}}){Text(t("delete"))}},
        dismissButton={TextButton(onClick={deleting=false}){Text(t("back"))}}
    )
}
@Composable private fun RadioButtonRow(label:String,selected:Boolean,onClick:()->Unit){Row{RadioButton(selected,onClick);TextButton(onClick=onClick){Text(label)}}}

@Composable internal fun OfflineBundleSummary(language:String,snapshot:BundleSnapshot){
    fun t(key:String)=offlineBundleText(language,key)
    Column{
        Text(t("members")+": ${snapshot.ready}/${snapshot.members.size}")
        snapshot.members.forEach{member->Text(member.name+" · "+t(bundleStateKey(member.state))+if(member.detail.isBlank())""else" · "+member.detail)}
        Text((if(snapshot.estimated)t("estimate")+": ≈ "else"")+"${snapshot.knownBytes/1024} KB"+(if(snapshot.unknownSizes>0)" + ${t("unknown")} (${snapshot.unknownSizes})"else""))
    }
}
