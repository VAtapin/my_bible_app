package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.readingText
import com.bibledesktop.myapp.ui.reading.ReadingViewport
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.BibleChapter
import com.bibledesktop.shared.api.BibleVerse
import com.bibledesktop.shared.api.BibleContentSource
import com.bibledesktop.shared.api.SavedPassage
import com.bibledesktop.shared.api.WordMark
import com.bibledesktop.shared.api.PersonalStudy
import com.bibledesktop.shared.api.explicitSourceStrongTokens
import com.bibledesktop.shared.api.sourceStrongNumbers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import com.bibledesktop.myapp.ui.study.InlineVerseCommentaries
import com.bibledesktop.myapp.ui.study.InlineVerseReferences

@Composable
internal fun ChapterReadingContent(
    language: String, chapter: BibleChapter, fontSize: Float, bookmarkedKeys: Set<String>,
    onBookmark: (BibleChapter, BibleVerse) -> Unit, onShare: (BibleChapter, BibleVerse) -> Unit,
    onNote: (BibleChapter, BibleVerse) -> Unit, modifier: Modifier = Modifier,
    onStudy: ((BibleChapter, BibleVerse) -> Unit)? = null, initialVerse: Int = 0,
    onVisibleRange: ((BibleVerse, BibleVerse) -> Unit)? = null,
    client: BibleContentSource? = null,
    onVisiblePlace: ((BibleChapter, BibleVerse, BibleVerse, Int) -> Unit)? = null,
    onPersonal: ((BibleChapter, BibleVerse) -> Unit)? = null,
    selection: SavedPassage? = null,
    onStrong: ((BibleChapter, BibleVerse, String) -> Unit)? = null,
) {
    if (client != null) {
        key(chapter.translation.code, chapter.book.slug, chapter.chapter.number, initialVerse) {
            ContinuousChapterContent(language, chapter, client, fontSize, bookmarkedKeys, onBookmark, onShare, onNote,
                onStudy, initialVerse, modifier, onVisiblePlace, onPersonal = onPersonal, selection = selection, onStrong = onStrong)
        }
        return
    }
    key(chapter.translation.code, chapter.book.slug, chapter.chapter.number) {
        ReadingViewport(modifier) {
            val listState = rememberLazyListState()
            LaunchedEffect(initialVerse) {
                chapter.verses.indexOfFirst { it.number == initialVerse }.takeIf { it >= 0 }?.let { listState.scrollToItem(it) }
            }
            LaunchedEffect(listState, chapter) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }.collectLatest { indices ->
                    delay(180)
                    val first = indices.firstOrNull()?.let { chapter.verses.getOrNull(it) }
                    val last = indices.lastOrNull()?.let { chapter.verses.getOrNull(it) }
                    if (first != null && last != null) onVisibleRange?.invoke(first, last)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                items(chapter.verses, key = BibleVerse::osisRef) { verse ->
                    VerseRow(language, chapter, verse, fontSize,
                        "${chapter.translation.code}:${verse.osisRef}" in bookmarkedKeys,
                        onBookmark = { onBookmark(chapter, verse) }, onShare = { onShare(chapter, verse) },
                        onNote = { onNote(chapter, verse) }, onStudy = onStudy?.let { { it(chapter, verse) } },
                        highlighted = initialVerse > 0 && verse.number == initialVerse, selection = selection,
                        onPersonal = onPersonal?.let { handler -> { handler(chapter,verse) } },
                        onStrong = onStrong?.let { handler -> { number -> handler(chapter,verse,number) } })
                }
            }
        }
    }
}

@Composable
internal fun VerseRow(
    language: String, chapter: BibleChapter, verse: BibleVerse, fontSize: Float, bookmarked: Boolean,
    onBookmark: () -> Unit, onShare: () -> Unit, onNote: () -> Unit,
    onStudy: (() -> Unit)? = null,
    highlighted: Boolean = false,
    onPersonal: (() -> Unit)? = null,
    selection: SavedPassage? = null,
    night: Boolean = false,
    onStrong: ((String) -> Unit)? = null,
) {
    val display=LocalReaderPreferences.current
    val readingNight=night||display.night
    val context = LocalContext.current
    val revision by PersonalStudyStore.changes.collectAsState()
    var personal by remember { mutableStateOf(PersonalStudy()) }
    LaunchedEffect(revision) { try { personal = PersonalStudyStore.read(context) } catch (_: Exception) { /* Editor reports damaged records; never block Bible reading. */ } }
    var wordNote by remember(verse.id){mutableStateOf<WordMark?>(null)}
    var menu by rememberSaveable(verse.osisRef) { mutableStateOf(false) }
    val body = remember(verse, chapter.translation.language.code) {
        if(verse.annotations?.let{it.status=="available"&&it.validFor(verse.plainText)}==true)verse.plainText else if (chapter.translation.language.code in setOf("cu", "cu-civil")) readingText(verse.text) else verse.plainText
    }
    val markedBody = remember(body, verse, personal, readingNight) {
        buildAnnotatedString {
            append(body)
            // CS typography can differ from plain_text; never move saved offsets onto another text.
            if (body == verse.plainText) personal.marks.filter { it.matches(chapter.translation.code, verse) }.forEach { mark ->
                val hex = personal.palette[mark.color]?.let { if (readingNight) it.night else it.day }
                val color = runCatching { hex?.let { Color(android.graphics.Color.parseColor(it)) } }.getOrNull() ?: Color.Transparent
                addStyle(SpanStyle(background = color, textDecoration = if (mark.underline) TextDecoration.Underline else null), mark.start, mark.end)
                if (mark.note.isNotBlank()) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), mark.start, mark.end)
            }
        }
    }
    val rangeBookmarks = personal.bookmarks.filter { it.passage.contains(chapter, verse) }
    val rangeSelected = selection?.contains(chapter, verse) == true
    Row(Modifier.fillMaxWidth().padding(vertical = if(display.separateVerses)6.dp else 0.dp).testTag("verse-${verse.number}")
        .semantics { selected = highlighted || rangeSelected }
        .background(if (highlighted || rangeSelected) LightBlue else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
        .then(if (onPersonal != null) Modifier.combinedClickable(onClick = {}, onLongClick = onPersonal) else Modifier),
        verticalAlignment = Alignment.Top) {
        Text((if(display.verseNumbers)verse.number.toString() else "") + if (rangeBookmarks.any { it.description.isNotBlank() }) "✎" else "", Modifier.padding(top = 6.dp).widthIn(min = if(display.verseNumbers)20.dp else if(rangeBookmarks.isNotEmpty())4.dp else 0.dp)
            .then(if (rangeBookmarks.isNotEmpty()) Modifier.background(runCatching { Color(android.graphics.Color.parseColor(personal.palette[rangeBookmarks.first().color]?.let { if (readingNight) it.night else it.day } ?: "#ffe49a")) }.getOrDefault(LightBlue)) else Modifier), color = PrimaryBlue,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
        SelectionContainer(Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            CompositionLocalProvider(LocalSourceAnnotationLanguage provides language){SourceVerseText(verse,if(body.isBlank()) androidx.compose.ui.text.AnnotatedString(localized(R.string.catalog_verse_missing,language))else markedBody,display,
                androidx.compose.ui.text.TextStyle(color=MaterialTheme.colorScheme.onSurface,fontFamily=readingFont(chapter.translation.language.code),fontSize=fontSize.sp,lineHeight=(fontSize*display.lineHeight).sp),wordNotes=personal.marks.filter{it.matches(chapter.translation.code,verse)},onWordNote={wordNote=it},onStrong={number->if(onStrong!=null)onStrong(number)else onStudy?.invoke()})}
        }
        if(display.strongNumbers && verse.hasStrongMarkup) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(2.dp)) { sourceStrongNumbers(verse.text,verse.hasStrongMarkup).filter{number->body!=verse.plainText||verse.explicitSourceStrongTokens().none{it.number==number}}.forEach { number -> TextButton(onClick={if(onStrong!=null)onStrong(number)else onStudy?.invoke()},modifier=Modifier.height(32.dp),contentPadding=PaddingValues(horizontal=4.dp)) {Text(number,style=MaterialTheme.typography.labelSmall)} } }
        }
        if(display.commentaryLinks&&onStudy!=null)InlineVerseCommentaries(language,chapter,verse,onStudy)
        if(display.crossReferences&&onStudy!=null)InlineVerseReferences(language,chapter,verse,onStudy)
        }
        if(!display.clean)Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.MoreVert,
                    localized(R.string.reader_verse_actions, language, verse.number), tint = PrimaryBlue)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (onPersonal != null) DropdownMenuItem(text = { Text(personalStudyText(language,"title")) }, onClick = { menu = false; onPersonal() })
                DropdownMenuItem(
                    text = { Text(localized(if (bookmarked) R.string.bible_bookmark_remove else R.string.bible_bookmark_add, language)) },
                    leadingIcon = { Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null) },
                    onClick = { menu = false; onBookmark() },
                )
                DropdownMenuItem(text = { Text(localized(R.string.bible_share, language)) },
                    leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text(localized(R.string.note_edit, language)) },
                    leadingIcon = { Icon(Icons.Outlined.EditNote, null) }, onClick = { menu = false; onNote() })
                if (onStudy != null) DropdownMenuItem(text = { Text(localized(R.string.study_verse, language)) },
                    leadingIcon = { Icon(Icons.Outlined.AutoStories, null) }, onClick = { menu = false; onStudy() })
            }
        }
    }
    wordNote?.let{WordMarkNoteDialog(language,it){wordNote=null}}
}
