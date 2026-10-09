package com.bibledesktop.myapp.ui.bible

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.ReadingViewport
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private data class StreamRow(val chapter: BibleChapter, val verse: BibleVerse?, val empty: Boolean = false) {
    val key: String get() = verse?.osisRef ?: "${chapter.book.slug}:${chapter.chapter.number}:${if (empty) "empty" else "heading"}"
}

/** Stable verse keys and LazyColumn keep prepended chapters anchored and long books virtualized. */
@Composable
internal fun ContinuousChapterContent(language: String, initial: BibleChapter, client: BibleContentSource, fontSize: Float,
    bookmarkedKeys: Set<String>, onBookmark: (BibleChapter, BibleVerse) -> Unit, onShare: (BibleChapter, BibleVerse) -> Unit,
    onNote: (BibleChapter, BibleVerse) -> Unit, onStudy: ((BibleChapter, BibleVerse) -> Unit)?, initialVerse: Int, modifier: Modifier,
    onVisiblePlace: ((BibleChapter, BibleVerse, BibleVerse, Int) -> Unit)?,
    followVerse: Int? = null, initialOffset: Int? = null, listTag: String = "continuous-reader", followRequest: Int = 0) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE) }
    val sections = remember(initial) { mutableStateMapOf(initial.chapter.number to initial) }
    var loading by remember(initial) { mutableStateOf(false) }
    var failed by remember(initial) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val rows = sections.toSortedMap().values.flatMap { chapter ->
        listOf(StreamRow(chapter, null)) + if (chapter.verses.isEmpty()) listOf(StreamRow(chapter, null, true)) else chapter.verses.map { StreamRow(chapter, it) }
    }
    val state = rememberLazyListState()
    LaunchedEffect(initial) {
        val index = rows.indexOfFirst { it.verse?.number == initialVerse && it.chapter.chapter.number == initial.chapter.number }.coerceAtLeast(0)
        val restore = preferences.getString("lastTranslation", null) == initial.translation.code && preferences.getString("lastBookSlug", null) == initial.book.slug &&
            preferences.getInt("lastChapter", 0) == initial.chapter.number && preferences.getInt("lastVerse", 0) == initialVerse
        state.scrollToItem(index, initialOffset?.coerceAtLeast(0) ?: if (restore) preferences.getInt("lastVerseOffset", 0).coerceAtLeast(0) else 0)
    }
    LaunchedEffect(followVerse, followRequest) {
        if (followVerse != null) rows.indexOfFirst { it.verse?.number == followVerse && it.chapter.chapter.number == initial.chapter.number }
            .takeIf { it >= 0 }?.let { state.scrollToItem(it) }
    }
    val firstNumber = sections.keys.min()
    val lastNumber = sections.keys.max()
    val nearEnd by remember { derivedStateOf { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= state.layoutInfo.totalItemsCount - 4 } == true } }
    val nearStart by remember { derivedStateOf { state.firstVisibleItemIndex <= 2 && state.layoutInfo.visibleItemsInfo.isNotEmpty() } }
    suspend fun load(number: Int, prepend: Boolean) {
        if (loading || failed || number in sections || number !in 1..initial.book.chaptersCount) return
        loading = true
        try {
            val value = client.getChapter(initial.translation.code, initial.book.slug, number)
            require(value.translation.code == initial.translation.code && value.book.slug == initial.book.slug && value.chapter.number == number)
            val osis = initial.verses.firstOrNull()?.osisRef?.substringBefore('.') ?: value.verses.firstOrNull()?.osisRef?.substringBefore('.')
            require(value.verses.all { it.osisRef == "$osis.$number.${it.number}" } && value.verses.map { it.osisRef }.distinct().size == value.verses.size)
            val index = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            sections[number] = value
            if (prepend) state.requestScrollToItem(index + 1 + value.verses.size.coerceAtLeast(1), offset)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(nearEnd, lastNumber, firstNumber, retry) { if (nearEnd) load(lastNumber + 1, false) }
    LaunchedEffect(nearStart, nearEnd, firstNumber, lastNumber, retry) { if (nearStart && !nearEnd) load(firstNumber - 1, true) }
    LaunchedEffect(state, sections.toMap()) {
        val byKey = rows.associateBy(StreamRow::key)
        snapshotFlow { state.layoutInfo.visibleItemsInfo.filter { it.offset + it.size > 0 && it.offset < state.layoutInfo.viewportEndOffset }.map { it.key to it.offset } }.collectLatest { items ->
            delay(180)
            val visible = items.mapNotNull { (key, offset) -> byKey[key]?.takeIf { it.verse != null }?.let { it to offset } }
            val top = visible.firstOrNull()?.first ?: return@collectLatest
            val last = visible.lastOrNull { it.first.chapter.chapter.number == top.chapter.chapter.number }?.first ?: top
            onVisiblePlace?.invoke(top.chapter, top.verse!!, last.verse!!, (-visible.first().second).coerceAtLeast(0))
        }
    }
    ReadingViewport(modifier) {
        Column(Modifier.fillMaxSize()) {
            if (failed) Row(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(localized(R.string.reader_continuation_error, language), Modifier.weight(1f))
                TextButton(onClick = { failed = false; retry++ }) { Text(localized(R.string.retry, language)) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag(listTag), state = state, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                items(rows, key = StreamRow::key) { row ->
                    if (row.verse == null) {
                        if (row.empty) Text(localized(R.string.bible_chapter_unavailable, language))
                        else Text("${row.chapter.book.name} ${row.chapter.chapter.number}", Modifier.padding(vertical = 12.dp).testTag("stream-chapter-${row.chapter.chapter.number}"), style = MaterialTheme.typography.titleMedium)
                    } else VerseRow(language, row.chapter, row.verse, fontSize, "${row.chapter.translation.code}:${row.verse.osisRef}" in bookmarkedKeys,
                        onBookmark = { onBookmark(row.chapter, row.verse) }, onShare = { onShare(row.chapter, row.verse) }, onNote = { onNote(row.chapter, row.verse) },
                        onStudy = onStudy?.let { { it(row.chapter, row.verse) } }, highlighted = row.chapter.chapter.number == initial.chapter.number && row.verse.number == initialVerse)
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
