package com.bibledesktop.myapp.ui.study

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.data.*
import com.bibledesktop.shared.api.DictionaryReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.io.File

private fun JsonObject.string(name: String) = get(name)?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.integer(name: String) = get(name)?.jsonPrimitive?.intOrNull
private fun JsonObject.sourceTitle()=(get("source_verse") as? JsonObject)?.string("osis_ref").orEmpty()+" → "+(get("target_verse") as? JsonObject)?.string("osis_ref").orEmpty()

/** Stable server keys open the complete installed source, independently from online numeric IDs. */
@Composable internal fun StudyPackageReader(language: String, pack: StudyOfflinePackage, onBack: () -> Unit, onReference: ((DictionaryReference) -> Unit)? = null) {
    val context = LocalContext.current; val text = dictionaryTexts(language); val scope = rememberCoroutineScope()
    val store = remember { StudyPackageStore(context) }; val preferences = remember { context.getSharedPreferences("study-package-reading:${pack.id}",Context.MODE_PRIVATE) }
    var bookId by rememberSaveable { mutableStateOf(preferences.getString("book", "").orEmpty()) }; var entryId by rememberSaveable { mutableStateOf(preferences.getString("entry", "").orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }; var submitted by rememberSaveable { mutableStateOf("") }; var offset by rememberSaveable { mutableIntStateOf(0) }
    var page by remember { mutableStateOf<StudyPackageRows?>(null) }; var article by remember { mutableStateOf<JsonObject?>(null) }; var alternatives by remember { mutableStateOf(emptyList<JsonObject>()) }
    var metadata by remember { mutableStateOf<JsonObject?>(null) }; var sources by remember { mutableStateOf(emptyList<JsonObject>()) }; var links by remember { mutableStateOf(emptyList<JsonObject>()) }; var references by remember { mutableStateOf(emptyList<JsonObject>()) }; var media by remember { mutableStateOf(emptyList<Pair<File,String>>()) }
    var unresolvedMedia by remember{mutableStateOf(emptyList<String>())}
    var capabilities by remember{mutableStateOf(emptyList<String>())}
    var busy by remember { mutableStateOf(false) }; var failed by remember { mutableStateOf(false) }; var retry by remember { mutableIntStateOf(0) }; var formsOnly by remember { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val catalogState = rememberLazyListState(); val articleState = rememberLazyListState(preferences.getInt("index",0),preferences.getInt("offset",0))
    fun open(id: String) { if (entryId.isNotBlank()) history = history + entryId; entryId = id; scope.launch { articleState.scrollToItem(0) } }
    fun back() { if (history.isNotEmpty()) { entryId = history.last(); history = history.dropLast(1) } else if (entryId.isNotBlank()) entryId = "" else if (bookId.isNotBlank()) { bookId = ""; offset = 0 } else onBack() }
    BackHandler(onBack = ::back)
    LaunchedEffect(entryId,bookId) { preferences.edit().putString("entry",entryId).putString("book",bookId).apply() }
    LaunchedEffect(articleState,entryId) { snapshotFlow { articleState.firstVisibleItemIndex to articleState.firstVisibleItemScrollOffset }.collectLatest { (index,offset) -> delay(180); if(entryId.isNotBlank())preferences.edit().putInt("index",index).putInt("offset",offset).apply() } }
    LaunchedEffect(pack.id,entryId,bookId,submitted,offset,retry) {
        busy=true;failed=false;article=null;links=emptyList();references=emptyList();media=emptyList();unresolvedMedia=emptyList()
        try {
            metadata=store.metadata(pack.id)
            val counts=listOf("entries","books","media","word_forms","references").map{it to store.rows(pack.id,it,limit=1).total}
            val names=when(language){"ru"->listOf("Статьи","Книги","Изображения","Словоформы","Библейские ссылки");"de"->listOf("Artikel","Bücher","Bilder","Wortformen","Bibelstellen");"uk"->listOf("Статті","Книги","Зображення","Словоформи","Біблійні посилання");else->listOf("Articles","Books","Images","Word forms","Scripture references")}
            capabilities=counts.mapIndexedNotNull{index,(_,count)->if(count>0)names[index]+": "+count else null}
            if(pack.kind=="strong")sources=store.rows(pack.id,"lexicons",limit=500).rows
            if(entryId.isNotBlank()) {
                alternatives=store.rows(pack.id,if(pack.kind=="cross_references")"references"else"entries",ids=listOf(entryId),limit=500).rows
                article=alternatives.firstOrNull()?:error("Entry missing")
                links=store.rows(pack.id,"links",ids=listOf(entryId),limit=500).rows
                references=if(pack.kind=="dictionary")store.rows(pack.id,"references",ids=listOf(entryId),limit=500).rows else if(pack.kind=="cross_references")listOfNotNull(article?.get("target_verse") as? JsonObject)else listOfNotNull(article?.takeIf { it.string("book_slug").isNotBlank() })
                val imageLinks=store.rows(pack.id,"media_links",ids=listOf(entryId),limit=500).rows
                media=imageLinks.mapNotNull { store.media(pack.id,it.string("media_id"))?.let{file->file to article?.string("topic").orEmpty()} }
                if(pack.kind=="commentary"){
                    val annotatedMedia=((article?.get("annotations") as? JsonObject)?.get("media") as? JsonArray).orEmpty().mapNotNull{it as? JsonObject}
                    unresolvedMedia=annotatedMedia.filter{it.string("status")!="resolved"}.map{studySourceName(it.string("alt").ifBlank{it.string("src")})}
                    media=annotatedMedia.filter{it.string("status")=="resolved"}.map{item->(store.media(pack.id,item.string("sha256"))?:error("Installed image missing")) to item.string("alt").ifBlank{item.string("src")}}
                }
            } else {
                val table=if(pack.kind=="cross_references")"references"else if(pack.kind=="commentary"&&bookId.isBlank())"books"else"entries"
                page=store.rows(pack.id,table,query=submitted,offset=offset,bookId=bookId.takeIf{it.isNotBlank()})
                formsOnly=pack.kind=="dictionary"&&store.rows(pack.id,"entries",limit=1).total==0
                if(formsOnly)page=store.rows(pack.id,"word_forms",query=submitted,offset=offset)
            }
        } catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){failed=true}finally{busy=false}
    }
    val bodyChunks by produceState<List<String>?>(null,article) { value=null;value=article?.let{prepareStudyBody(it.string("body").ifBlank{it.string("content")})} }
    LazyColumn(Modifier.fillMaxSize(),state=if(entryId.isBlank())catalogState else articleState,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth()){TextButton(onClick=::back){Text(text.back)};Text(metadata?.string("name").orEmpty().ifBlank{pack.id},style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))};Text(pack.kind+" · "+pack.version.take(8)) }
        metadata?.let{source->item{ModuleSourceCard(language,source,pack.version,capabilities)}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(failed)item{Text(text.error);TextButton(onClick={retry++}){Text(text.retry)}}
        article?.let { body ->
            item {Text(if(pack.kind=="cross_references")body.sourceTitle()else body.string("topic").ifBlank{body.string("title").ifBlank{body.string("id")}},style=MaterialTheme.typography.titleLarge);if(body.string("author").isNotBlank())Text(body.string("author"));val source=sources.find{it.string("code")==body.string("lexicon_code")};if(source!=null)Text(source.string("name")+" · "+source.string("language"));if(body.string("word").isNotBlank())Text(body.string("word")+" · "+body.string("transliteration"))}
            if(bodyChunks==null)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            itemsIndexed(bodyChunks.orEmpty()){_,chunk->androidx.compose.foundation.text.selection.SelectionContainer{Text(chunk)}}
            if(alternatives.size>1) item {
                Row { alternatives.forEach { entry ->
                    FilterChip(selected=article==entry,onClick={article=entry},label={Text(sources.find{it.string("code")==entry.string("lexicon_code")}?.string("name")?:entry.string("lexicon_code"))})
                } }
            }
            itemsIndexed(media){_,image->AtlasImage(image.first,studySourceName(image.second),language)}
            itemsIndexed(unresolvedMedia){_,label->Text(label+" · "+when(language){"ru"->"Изображение не сохранено в пакете";"de"->"Abbildung nicht im Paket gespeichert";"uk"->"Зображення не збережене в пакеті";else->"Image not preserved in the package"})}
            itemsIndexed(links){_,link->TextButton(onClick={open(link.string("target_id"))}){Text(link.string("label").ifBlank{link.string("target_id")})}}
            itemsIndexed(references){_,ref->val chapter=ref.integer("chapter")?:ref.integer("chapter_from");val first=ref.integer("verse_from")?:ref.integer("verse");val last=ref.integer("verse_to");val label=ref.string("book_slug")+(chapter?.let{" $it"}?:"")+(first?.takeIf{it>0}?.let{":$it"}?:"")+(last?.takeIf{it>0&&it!=first}?.let{"–$it"}?:"");if(onReference!=null)TextButton(onClick={onReference(DictionaryReference(ref.string("book_slug"),chapter,first,last))}){Text(label)}else Text(label)}
        }
        if(entryId.isBlank()) {
            item {OutlinedTextField(query,{query=it},label={Text(text.search)},singleLine=true,modifier=Modifier.fillMaxWidth());TextButton(onClick={submitted=query;offset=0;scope.launch{catalogState.scrollToItem(0)}}){Text(text.search)};Text("${offset+1}–${offset+(page?.rows?.size?:0)} / ${page?.total?:0}")}
            itemsIndexed(page?.rows.orEmpty()){_,row->if(formsOnly)Text(row.string("variation")+" → "+row.string("standard_form"))else OutlinedButton(onClick={if(pack.kind=="commentary"&&bookId.isBlank()){bookId=row.string("id");offset=0}else open(row.string("id"))},modifier=Modifier.fillMaxWidth()){Text(if(pack.kind=="cross_references")row.sourceTitle()else row.string("topic").ifBlank{row.string("title").ifBlank{row.string("id")}})}}
            item {Row{TextButton(onClick={offset=(offset-30).coerceAtLeast(0);scope.launch{catalogState.scrollToItem(0)}},enabled=offset>0&&!busy){Text(text.back)};TextButton(onClick={offset+=30;scope.launch{catalogState.scrollToItem(0)}},enabled=offset+(page?.rows?.size?:0)<(page?.total?:0)&&!busy){Text(text.next)}}}
        }
    }
}
