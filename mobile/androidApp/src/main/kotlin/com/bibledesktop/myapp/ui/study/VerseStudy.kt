package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.readingText
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import com.bibledesktop.myapp.ui.bible.BibleSearchScreen
import com.bibledesktop.myapp.data.OfflineContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData

@Composable
internal fun VerseStudyDialog(language: String, chapter: BibleChapter, verse: BibleVerse, client: BibleContentSource,
    onOpen: (ReferenceTarget) -> Unit, onClose: () -> Unit, initialStrong: String? = null) {
    var temporary by remember { mutableStateOf<List<ReferenceTarget>?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = 720.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${chapter.book.name} ${chapter.chapter.number}:${verse.number}", style = MaterialTheme.typography.titleLarge)
                VerseStudy(language, chapter, verse, client, onTemporary = { temporary = it.targets }, initialStrong = initialStrong, onOpen = onOpen)
                TextButton(onClick = onClose) { Text(localized(R.string.study_close, language)) }
            }
        }
    }
    temporary?.let { TemporaryStudyPassage(language, chapter.translation.code, it, client) { temporary = null } }
}

@Composable
internal fun VerseStudy(language: String, chapter: BibleChapter, verse: BibleVerse, client: BibleContentSource,
    onTemporary: ((ReferenceGroup) -> Unit)? = null, initialStrong: String? = null, onOpen: (ReferenceTarget) -> Unit) {
    val labels = studyTexts(language)
    val context=LocalContext.current
    val referenceStore=remember(context){ReferenceDisplayStore(context)}
    val referenceRevision by ReferenceDisplayStore.changes.collectAsState()
    val referenceSettings=remember(referenceRevision){referenceStore.load()}
    val referenceText=referenceDisplayTexts(language)
    var books by remember(chapter.translation.code) { mutableStateOf<List<BibleBook>>(emptyList()) }
    var canonicalBook by remember(chapter.translation.code, chapter.book.slug) { mutableStateOf<String?>(null) }
    var dictionary by remember { mutableStateOf<Pair<String, String>?>(null) }
    var references by remember(verse.id, chapter.translation.code) { mutableStateOf<CrossReferences?>(null) }
    var referenceError by remember(verse.id, chapter.translation.code) { mutableStateOf(false) }
    var tokens by remember(verse.id, chapter.translation.code) { mutableStateOf<StrongTokens?>(null) }
    var tokenError by remember(verse.id, chapter.translation.code) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val sourceTokens = remember(verse) { verse.sourceStudyStrongTokens() }
    val hasStrong = chapter.translation.hasStrong || verse.hasStrongMarkup || sourceTokens.isNotEmpty() || initialStrong != null
    LaunchedEffect(chapter.translation.code) { try { books = client.getBooks(chapter.translation.code) }
        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { books = emptyList() } }
    LaunchedEffect(chapter.translation.code, chapter.book.slug) { try {
        val canon = client.getTranslations().firstOrNull { it.code == chapter.translation.code }?.canonCode
        if (canon != null) canonicalBook = client.getCanonicalSlug(canon, verse.osisRef.substringBefore('.'))
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { canonicalBook = null } }
    LaunchedEffect(verse.id, chapter.translation.code, retry) {
        references = null; referenceError = false
        try { references = ((client as? OfflineContentRepository)?.getCrossReferencesAt(verse.id,chapter.translation.code,verse.osisRef)?:client.getCrossReferences(verse.id, chapter.translation.code)).also { require(it.verse.osisRef == verse.osisRef&&it.translationCode==chapter.translation.code) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { referenceError = true }
    }
    LaunchedEffect(verse.id, chapter.translation.code, retry, hasStrong) {
        tokens = null; tokenError = false
        if (sourceTokens.isNotEmpty()) tokens = StrongTokens(StudyVerse(verse.id, verse.osisRef), sourceTokens)
        else if (hasStrong) try { tokens = client.getStrongTokens(verse.id, chapter.translation.code) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { tokenError = true }
    }
    Column(Modifier.fillMaxWidth().testTag("verse-study"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectionContainer { Text(verse.plainText, fontFamily = readingFont(chapter.translation.language.code), fontSize = 19.sp, lineHeight = 29.sp) }
        Text(chapter.translation.name, color = PrimaryBlue)
        CommentaryPanel(language, chapter, verse, client)
        DictionaryContext(language, canonicalBook.orEmpty(), verse.osisRef.split('.')[1].toInt(), listOf(verse.id), verseNumbers = listOf(verse.osisRef.split('.')[2].toInt()), verseChapters = listOf(verse.osisRef.split('.')[1].toInt()), osis = verse.osisRef.substringBefore('.')) { module, key -> dictionary = module to key }
        Text(localized(R.string.study_references, language), style = MaterialTheme.typography.titleMedium)
        if (referenceError) StudyError(language) { retry++ }
        else if (references == null) CircularProgressIndicator(Modifier.size(24.dp))
        else if (references!!.references.isEmpty()) Text(localized(R.string.study_no_references, language))
        val sources = references?.references?.mapNotNull { it.source }?.distinct().orEmpty()
        Text(referenceText.detail)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = referenceSettings.detailSources == null, onClick = { referenceStore.save(referenceSettings.copy(detailSources=null)) }, label = { Text(labels.all) })
            sources.forEach { source -> FilterChip(selected = referenceSettings.detailSources==null||source in referenceSettings.detailSources, onClick = { referenceStore.save(referenceSettings.copy(detailSources=toggledReferenceSources(referenceSettings.detailSources,source,sources))) }, label = { Text(source) }) }
            FilterChip(selected = referenceSettings.bySource, onClick = { referenceStore.save(referenceSettings.copy(bySource=!referenceSettings.bySource)) }, label = { Text(labels.sources) })
        }
        val groups = displayReferenceGroups(references?.references.orEmpty(),referenceSettings.detailSources, books.mapNotNull { it.canonicalBook?.osisCode?.let { code -> code to it.order } }.toMap())
            .let { if (referenceSettings.bySource) it.sortedBy(ReferenceGroup::source) else it }
        if(references!=null)Text("${referenceText.count}: ${groups.size}")
        groups.forEach { group ->
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(group.label, style = MaterialTheme.typography.titleMedium)
                    ReferenceNumbering(language,group.targets)
                    if (group.source.isNotBlank()) Text("${group.source} · ${group.type}", style = MaterialTheme.typography.labelSmall)
                    Text(chapter.translation.name,style=MaterialTheme.typography.labelSmall)
                    group.targets.forEach { target -> SelectionContainer { Text("${target.verseNumber} ${referencePreviewText(target,localized(R.string.study_missing_text, language))}", fontFamily = readingFont(chapter.translation.language.code), fontSize = 18.sp, lineHeight = 27.sp) } }
                    TextButton(onClick = { if (onTemporary != null) onTemporary(group) else onOpen(group.targets.first()) }, modifier = Modifier.testTag("reference-${group.targets.first().osisRef}")) {
                        Text(localized(R.string.bookmark_open, language))
                    }
                    TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText(group.label,referenceCopyText(group,chapter.translation.name)))}) {Text(referenceText.copy)}
                }
            }
        }
        Text(localized(R.string.study_strong, language), style = MaterialTheme.typography.titleMedium)
        if (!hasStrong) Text(localized(R.string.study_no_strong, language))
        else if (tokenError && initialStrong == null) StudyError(language) { retry++ }
        else if (initialStrong != null) StrongWords(language, chapter, verse.id, tokens?.tokens.orEmpty(), null, client, initialStrong)
        else if (tokens == null) CircularProgressIndicator(Modifier.size(24.dp))
        else if (tokens!!.tokens.isEmpty()) Text(localized(R.string.study_no_strong, language))
        else StrongWords(language, chapter, verse.id, tokens!!.tokens, books.firstOrNull { it.slug == chapter.book.slug }?.canonicalBook?.testament, client, initialStrong)
        Text(localized(R.string.study_offline_hint, language), color = PrimaryBlue, style = MaterialTheme.typography.bodySmall)
    }
    dictionary?.let { article -> Dialog(onDismissRequest = { dictionary = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { DictionaryLibrary(language, client, chapter.translation.code, onBack = { dictionary = null }, initialModule = article.first, initialEntry = article.second) }
    } }
}

@Composable
private fun StrongWords(language: String, chapter: BibleChapter, verseId: Long, tokens: List<StrongToken>, testament: String?, client: BibleContentSource, initialStrong: String?) {
    val labels = studyTexts(language)
    val numbers = tokens.mapNotNull { explicitStrongNumber(it.number, testament) }.distinct()
    var number by remember(verseId) { mutableStateOf(initialStrong) }
    var occurrences by remember { mutableStateOf<String?>(null) }
    var entry by remember(verseId, number) { mutableStateOf<StrongEntry?>(null) }
    var error by remember(verseId, number) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var installedSources by remember(verseId,number) {mutableStateOf(emptyList<StrongEntry>())}
    LaunchedEffect(verseId, number, retry) {
        entry = null; error = false
        val selected = number ?: return@LaunchedEffect
        try { installedSources=(client as? OfflineContentRepository)?.installedStrongEntries(selected).orEmpty(); entry=installedSources.firstOrNull()?:client.getStrongEntry(selected, verseId) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        numbers.forEach { value -> FilterChip(selected = value == number, onClick = { number = value }, label = { Text(value) }) }
    }
    if (numbers.isEmpty() && tokens.isNotEmpty()) Text(labels.unknownStrong)
    if(installedSources.size>1)FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){installedSources.forEach{source->FilterChip(selected=source.lexicon.code==entry?.lexicon?.code,onClick={entry=source},label={Text(source.lexicon.name+" · "+source.lexicon.language.uppercase())})}}
    if (number != null) {
        if (error) StudyError(language) { retry++ }
        else if (entry == null) CircularProgressIndicator(Modifier.size(24.dp))
        else Card {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(listOfNotNull(entry!!.number, entry!!.word, entry!!.transliteration).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                Text("${entry!!.lexicon.name} · ${entry!!.lexicon.language.uppercase()}", color = PrimaryBlue)
                SelectionContainer { Text(readingText(entry!!.content.orEmpty()).ifBlank { localized(R.string.study_missing_text, language) }, fontSize = 18.sp, lineHeight = 27.sp) }
                entry!!.pronunciation?.takeIf { it.isNotBlank() }?.let { Text(it) }
                TextButton(onClick = { occurrences = number }, modifier = Modifier.testTag("strong-occurrences")) { Text(labels.occurrences) }
            }
        }
    }
    occurrences?.let { value -> StrongOccurrences(language, chapter.translation.code, value, client) { occurrences = null } }
}

@Composable
private fun StrongOccurrences(language: String, code: String, number: String, client: BibleContentSource, onClose: () -> Unit) {
    var temporary by remember { mutableStateOf<ReferenceTarget?>(null) }
    var temporaryCode by remember { mutableStateOf(code) }
    var failure by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) { BibleSearchScreen(language, client, code, onBack = onClose, onOpen = { hit ->
            scope.launch { try {
                val chapter = client.getChapter(hit.translation, hit.book, hit.chapter)
                val verse = chapter.verses.first { it.number == hit.verse }
                temporaryCode = hit.translation
                temporary = ReferenceTarget(verse.id, verse.osisRef, hit.reference, hit.book, hit.chapter, hit.verse, verse.plainText)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failure = true } }
        }, preferencesName = "strong-search:$code:$number", initialQuery = number, initialMatch = VerseSearchMatch.STRONG) }
    }
    if (failure) AlertDialog(onDismissRequest = { failure = false }, text = { Text(studyTexts(language).missing) }, confirmButton = { TextButton(onClick = { failure = false }) { Text(localized(R.string.study_close, language)) } })
    temporary?.let { TemporaryStudyPassage(language, temporaryCode, listOf(it), client) { temporary = null } }
}

@Composable
internal fun StudyError(language: String, retry: () -> Unit) {
    Text(localized(R.string.study_error, language))
    TextButton(onClick = retry) { Text(localized(R.string.retry, language)) }
}
