package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.readingText
import com.bibledesktop.myapp.ui.reading.ReadingViewport
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.BibleChapter
import com.bibledesktop.shared.api.BibleVerse

@Composable
internal fun ChapterReadingContent(
    language: String, chapter: BibleChapter, fontSize: Float, bookmarkedKeys: Set<String>,
    onBookmark: (BibleChapter, BibleVerse) -> Unit, onShare: (BibleChapter, BibleVerse) -> Unit,
    onNote: (BibleChapter, BibleVerse) -> Unit, modifier: Modifier = Modifier,
    onStudy: ((BibleChapter, BibleVerse) -> Unit)? = null, initialVerse: Int = 0,
) {
    key(chapter.translation.code, chapter.book.slug, chapter.chapter.number) {
        ReadingViewport(modifier) {
            val listState = rememberLazyListState()
            LaunchedEffect(initialVerse) {
                chapter.verses.indexOfFirst { it.number == initialVerse }.takeIf { it >= 0 }?.let { listState.scrollToItem(it) }
            }
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                items(chapter.verses, key = BibleVerse::osisRef) { verse ->
                    VerseRow(language, chapter, verse, fontSize,
                        "${chapter.translation.code}:${verse.osisRef}" in bookmarkedKeys,
                        onBookmark = { onBookmark(chapter, verse) }, onShare = { onShare(chapter, verse) },
                        onNote = { onNote(chapter, verse) }, onStudy = onStudy?.let { { it(chapter, verse) } },
                        highlighted = initialVerse > 0 && verse.number == initialVerse)
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
) {
    var menu by rememberSaveable(verse.osisRef) { mutableStateOf(false) }
    val body = remember(verse, chapter.translation.language.code) {
        if (chapter.translation.language.code in setOf("cu", "cu-civil")) readingText(verse.text) else verse.plainText
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("verse-${verse.number}")
        .semantics { selected = highlighted }
        .background(if (highlighted) LightBlue else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.Top) {
        Text(verse.number.toString(), Modifier.padding(top = 6.dp).widthIn(min = 20.dp), color = PrimaryBlue,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
        SelectionContainer(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(body.ifBlank { localized(R.string.catalog_verse_missing, language) }, color = Ink, fontFamily = readingFont(chapter.translation.language.code),
                fontSize = fontSize.sp, lineHeight = (fontSize * 1.5f).sp)
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                Icon(if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.MoreVert,
                    localized(R.string.reader_verse_actions, language, verse.number), tint = PrimaryBlue)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
}
