package com.bibledesktop.myapp.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.setup.localized
import com.bibledesktop.myapp.ui.theme.*
import com.bibledesktop.shared.api.*
import kotlinx.coroutines.CancellationException

/** Selection is staged: dismissing never changes the currently open passage. */
@Composable
internal fun PassagePicker(language: String, translations: List<TranslationSummary>, client: BibleContentSource,
    translationCode: String, book: BibleBook?, chapter: Int?, startWithBooks: Boolean,
    onSelect: (String, BibleBook, Int) -> Unit, onClose: () -> Unit, onHome: () -> Unit,
    initialBookSlug: String? = book?.slug) {
    var code by rememberSaveable { mutableStateOf(translationCode) }
    // On recreation the catalogue may still be loading; the reader's saved identity is already available.
    var slug by rememberSaveable { mutableStateOf(if (startWithBooks) null else initialBookSlug) }
    var books by remember(code) { mutableStateOf<List<BibleBook>?>(null) }
    var error by remember(code) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(code, retry) {
        books = null; error = false
        if (code.isBlank()) return@LaunchedEffect
        try { books = client.getBooks(code).sortedBy(BibleBook::order) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = true }
    }
    val selectedBook = books?.firstOrNull { it.slug == slug }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 1000.dp).fillMaxSize(), color = Cream) {
            if (selectedBook == null) BookPicker(language, translations, code, books, error,
                onTranslationChange = { code = it; slug = null }, onBookClick = { slug = it.slug },
                onRetry = { retry++ }, onBack = onClose, onHome = onHome)
            else ChaptersScreen(language, selectedBook, onChapterClick = { onSelect(code, selectedBook, it) },
                onBack = onClose, onHome = onHome,
                textLanguage = translations.firstOrNull { it.code == code }?.language?.code.orEmpty(),
                currentChapter = chapter.takeIf { code == translationCode && selectedBook.slug == book?.slug },
                onChooseBook = { slug = null })
        }
    }
}

@Composable
internal fun ChaptersScreen(language: String, book: BibleBook, onChapterClick: (Int) -> Unit,
    onBack: () -> Unit, onHome: () -> Unit, textLanguage: String = "",
    currentChapter: Int? = null, onChooseBook: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        ReadingHeader(book.name, language, onBack, onHome, readingFont(textLanguage))
        onChooseBook?.let { action ->
            TextButton(onClick = action, modifier = Modifier.padding(horizontal = 12.dp).testTag("picker-choose-book")) {
                Text(localized(R.string.bible_choose_book, language))
            }
        }
        Text(localized(R.string.bible_chapters, language), Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            color = Ink, fontFamily = ReadingSerif, fontSize = 24.sp)
        LazyVerticalGrid(columns = GridCells.Adaptive(58.dp), modifier = Modifier.weight(1f).testTag("chapter-grid"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items((1..book.chaptersCount).toList()) { number ->
                Surface(Modifier.size(58.dp).testTag("choose-chapter-$number").clickable { onChapterClick(number) },
                    shape = RoundedCornerShape(16.dp), color = if (number == currentChapter) LightBlue else Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (number == currentChapter) Navy else WarmBorder)) {
                    Box(contentAlignment = Alignment.Center) { Text(number.toString(), color = Navy) }
                }
            }
        }
    }
}
