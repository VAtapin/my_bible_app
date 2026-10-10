package com.bibledesktop.myapp.ui.study

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.*
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun studyReadingText(body: String): String {
    val safe = body.replace(Regex("<(script|style|iframe|object)\\b[^>]*>[\\s\\S]*?</\\1\\s*>", RegexOption.IGNORE_CASE), "")
    return if (Regex("</?[A-Za-z][^>]*>").containsMatchIn(safe)) readingText(safe) else safe
}
internal fun studySourceName(value:String) = studyReadingText("<span>$value</span>")

@Composable
internal fun BooksScreen(language: String, client: BibleContentSource, onBack: () -> Unit, preferencesName: String = "bible-desktop-study") {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }
    var bookId by rememberSaveable { mutableLongStateOf(preferences.getLong("book", 0)) }
    var sectionId by rememberSaveable { mutableLongStateOf(preferences.getLong("section", 0)) }
    var offset by rememberSaveable { mutableIntStateOf(preferences.getInt("offset", 0)) }
    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var catalogOffset by rememberSaveable { mutableIntStateOf(0) }
    var books by remember { mutableStateOf<StudyBookPage?>(null) }
    var sourceNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var contents by remember { mutableStateOf<BookContents?>(null) }
    var article by remember { mutableStateOf<StudySection?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(retry) {
        try { sourceNames = client.getCommentaryModules().associate { it.code to it.name } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Optional source metadata must not hide an available book. */ }
    }
    fun back() { when { sectionId > 0 -> sectionId = 0; bookId > 0 -> { bookId = 0; offset = 0 }; else -> onBack() } }
    BackHandler { back() }
    LaunchedEffect(bookId, sectionId, offset, catalogOffset, submittedQuery, retry) {
        loading = true; failed = false; contents = null; article = null; books = null
        try {
            if (bookId == 0L) books = client.getStudyBooks(submittedQuery, catalogOffset)
            else {
                contents = client.getBookContents(bookId, offset)
                if (sectionId > 0) {
                    article = client.getBookSection(bookId, sectionId)
                    preferences.edit().putLong("book", bookId).putLong("section", sectionId).putInt("offset", offset).apply()
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    fun move(delta: Int) {
        val data = contents ?: return
        val index = data.sections.indexOfFirst { it.id == sectionId }; if (index < 0) return
        scope.launch {
            try {
                val next = index + delta
                if (next in data.sections.indices) sectionId = data.sections[next].id
                else {
                    loading = true
                    val nextOffset = if (delta < 0) (offset - 20).coerceAtLeast(0) else offset + data.sections.size
                    val page = client.getBookContents(bookId, nextOffset)
                    val section = if (delta < 0) page.sections.lastOrNull() else page.sections.firstOrNull()
                    if (section != null) { offset = nextOffset; sectionId = section.id }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { loading = false }
        }
    }
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        ReadingHeader(localized(R.string.study_books, language), language, { back() }, onBack)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Column(Modifier.padding(16.dp)) {
            Text(localized(R.string.study_material_error, language))
            TextButton(onClick = { retry++ }) { Text(localized(R.string.retry, language)) }
        }
        if (bookId == 0L) {
            OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth().padding(12.dp).testTag("books-search"),
                label = { Text(localized(R.string.study_book_search, language)) }, singleLine = true)
            Button(onClick = { submittedQuery = query.trim(); catalogOffset = 0; retry++ }, modifier = Modifier.padding(horizontal = 12.dp)) { Text(localized(R.string.study_book_search, language)) }
            books?.let { page ->
                Text("${localized(R.string.study_total, language)}: ${page.total}", Modifier.padding(12.dp))
                if (page.data.isEmpty()) Text(localized(R.string.study_material_empty, language), Modifier.padding(12.dp))
                LazyColumn(Modifier.weight(1f).testTag("books-catalog"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(page.data, key = StudyBook::id) { book -> Card(onClick = { bookId = book.id; sectionId = 0; offset = 0 }, modifier = Modifier.fillMaxWidth().testTag("study-book-${book.id}")) {
                        Column(Modifier.padding(16.dp)) { Text(book.title, style = MaterialTheme.typography.titleMedium); Text(listOfNotNull(book.author, sourceNames[book.moduleCode]).joinToString(" · ")) }
                    } }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { catalogOffset = (catalogOffset - 20).coerceAtLeast(0) }, enabled = catalogOffset > 0 && !loading) { Text(localized(R.string.study_previous, language)) }
                    TextButton(onClick = { catalogOffset += page.data.size }, enabled = catalogOffset + page.data.size < page.total && !loading) { Text(localized(R.string.study_next, language)) }
                }
            }
        } else contents?.let { data ->
            Text(data.book.title, Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge)
            Text(listOfNotNull(data.book.author, sourceNames[data.book.moduleCode]).joinToString(" · "), Modifier.padding(horizontal = 12.dp))
            if (article != null) {
                val section = article!!
                key(bookId, section.id) {
                    if(section.body.orEmpty().length>=largeStudyBodyThreshold){
                        ReadingViewport(Modifier.weight(1f)){
                            LargeStudyBody(section.body.orEmpty(),Modifier.fillMaxSize().padding(18.dp).testTag("study-book-body"),
                                preferences=preferences,positionKey="position:$bookId:${section.id}",
                                style=TextStyle(color=Ink,fontSize=19.sp,lineHeight=29.sp,fontFamily=ReadingSerif),
                                leading={Text(section.title?:localized(R.string.study_section,language),style=MaterialTheme.typography.titleLarge);section.author?.let{Text(it,color=PrimaryBlue)}},
                                trailing={CommentaryAnnotationsContent(language,section.annotations,client,moduleCode=data.book.moduleCode)})
                        }
                    }else{
                    val scroll = rememberScrollState(preferences.getInt("position:$bookId:${section.id}", 0))
                    LaunchedEffect(scroll.value) { preferences.edit().putInt("position:$bookId:${section.id}", scroll.value).apply() }
                    ReadingViewport(Modifier.weight(1f)) { SelectionContainer { Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(18.dp)) {
                        Text(section.title ?: localized(R.string.study_section, language), style = MaterialTheme.typography.titleLarge)
                        section.author?.let { Text(it, color = PrimaryBlue) }
                        Text(studyReadingText(section.body.orEmpty()), Modifier.testTag("study-book-body"), color = Ink, fontSize = 19.sp, lineHeight = 29.sp, fontFamily = ReadingSerif)
                        CommentaryAnnotationsContent(language,section.annotations,client,moduleCode=data.book.moduleCode)
                    } } }
                    }
                }
                val index = data.sections.indexOfFirst { it.id == sectionId }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { move(-1) }, enabled = !loading && index >= 0 && offset + index > 0) { Text(localized(R.string.study_previous, language)) }
                    TextButton(onClick = { sectionId = 0 }) { Text(localized(R.string.study_contents, language)) }
                    TextButton(onClick = { move(1) }, enabled = !loading && index >= 0 && offset + index + 1 < data.total) { Text(localized(R.string.study_next, language)) }
                }
            } else {
                Text("${localized(R.string.study_contents, language)} · ${data.total}", Modifier.padding(12.dp))
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(data.sections, key = StudySection::id) { section -> Card(onClick = { sectionId = section.id }, modifier = Modifier.fillMaxWidth().testTag("study-section-${section.id}")) {
                        Column(Modifier.padding(16.dp)) { Text(section.title ?: localized(R.string.study_section, language)); section.author?.let { Text(it, color = PrimaryBlue) } }
                    } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { offset = (offset - 20).coerceAtLeast(0) }, enabled = !loading && offset > 0) { Text(localized(R.string.study_previous, language)) }
                    TextButton(onClick = { offset += data.sections.size }, enabled = !loading && offset + data.sections.size < data.total) { Text(localized(R.string.study_next, language)) }
                }
            }
        }
        Text(localized(R.string.study_cache_hint, language), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}
