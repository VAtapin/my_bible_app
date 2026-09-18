package com.bibledesktop.myapp.ui.bible

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.myapp.ui.theme.Ink
import com.bibledesktop.myapp.ui.theme.LightBlue
import com.bibledesktop.myapp.ui.theme.Navy
import com.bibledesktop.myapp.ui.theme.PrimaryBlue
import com.bibledesktop.myapp.ui.theme.WarmBorder
import com.bibledesktop.shared.api.BibleApiClient
import com.bibledesktop.shared.api.BibleBook
import com.bibledesktop.shared.api.BibleChapter
import com.bibledesktop.shared.api.BibleVerse
import com.bibledesktop.shared.api.TranslationSummary
import java.util.Locale

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data object Error : LoadState<Nothing>
}

@Composable
fun BibleReader(
    language: String,
    translations: List<TranslationSummary>,
    client: BibleApiClient,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
    }
    var translationCode by rememberSaveable {
        val saved = preferences.getString("lastTranslation", null)
        mutableStateOf(
            saved?.takeIf { value -> translations.any { it.code == value } }
                ?: translations.firstOrNull()?.code.orEmpty(),
        )
    }
    var selectedBookSlug by rememberSaveable {
        mutableStateOf(preferences.getString("lastBookSlug", null))
    }
    var selectedChapter by rememberSaveable {
        mutableStateOf(preferences.getInt("lastChapter", 0).takeIf { it > 0 })
    }
    var booksState by remember { mutableStateOf<LoadState<List<BibleBook>>>(LoadState.Loading) }
    var chapterState by remember { mutableStateOf<LoadState<BibleChapter>>(LoadState.Loading) }
    var booksRetry by remember { mutableStateOf(0) }
    var chapterRetry by remember { mutableStateOf(0) }
    var fontSize by rememberSaveable { mutableFloatStateOf(preferences.getFloat("readerFontSize", 19f)) }
    var bookmarkEntries by remember { mutableStateOf(BookmarkStore.load(context)) }

    LaunchedEffect(translations, translationCode) {
        if (translationCode.isBlank() || translations.none { it.code == translationCode }) {
            translationCode = translations.firstOrNull()?.code.orEmpty()
        }
    }

    LaunchedEffect(translationCode, booksRetry) {
        if (translationCode.isBlank()) return@LaunchedEffect
        booksState = LoadState.Loading
        booksState = runCatching { client.getBooks(translationCode) }
            .fold(
                onSuccess = { LoadState.Ready(it.sortedBy(BibleBook::order)) },
                onFailure = { LoadState.Error },
            )
    }

    LaunchedEffect(translationCode, selectedBookSlug, selectedChapter, chapterRetry) {
        val bookSlug = selectedBookSlug ?: return@LaunchedEffect
        val chapterNumber = selectedChapter ?: return@LaunchedEffect
        chapterState = LoadState.Loading
        chapterState = runCatching {
            client.getChapter(translationCode, bookSlug, chapterNumber)
        }.fold(
            onSuccess = { LoadState.Ready(it) },
            onFailure = { LoadState.Error },
        )
    }

    LaunchedEffect(translationCode, selectedBookSlug, selectedChapter) {
        val bookSlug = selectedBookSlug ?: return@LaunchedEffect
        val chapterNumber = selectedChapter ?: return@LaunchedEffect
        preferences.edit()
            .putString("lastTranslation", translationCode)
            .putString("lastBookSlug", bookSlug)
            .putInt("lastChapter", chapterNumber)
            .apply()
    }

    val selectedBook = (booksState as? LoadState.Ready)?.value
        ?.firstOrNull { it.slug == selectedBookSlug }

    BackHandler {
        when {
            selectedChapter != null -> selectedChapter = null
            selectedBookSlug != null -> selectedBookSlug = null
            else -> onBack()
        }
    }

    when {
        selectedBook == null -> BooksScreen(
            language = language,
            translations = translations,
            selectedTranslationCode = translationCode,
            state = booksState,
            onTranslationChange = {
                translationCode = it
                selectedBookSlug = null
                selectedChapter = null
            },
            onBookClick = {
                selectedBookSlug = it.slug
                selectedChapter = null
            },
            onRetry = { booksRetry += 1 },
            onBack = onBack,
        )

        selectedChapter == null -> ChaptersScreen(
            language = language,
            book = selectedBook,
            onChapterClick = { selectedChapter = it },
            onBack = { selectedBookSlug = null },
        )

        else -> {
            val chapterNumber = selectedChapter ?: 1
            ChapterScreen(
            language = language,
            state = chapterState,
            chapterNumber = chapterNumber,
            chaptersCount = selectedBook.chaptersCount,
            fontSize = fontSize,
            bookmarkedKeys = bookmarkEntries.map { "${it.translationCode}:${it.reference}" }.toSet(),
            onBack = { selectedChapter = null },
            onRetry = { chapterRetry += 1 },
            onPrevious = { selectedChapter = (chapterNumber - 1).coerceAtLeast(1) },
            onNext = { selectedChapter = (chapterNumber + 1).coerceAtMost(selectedBook.chaptersCount) },
            onFontSmaller = {
                fontSize = (fontSize - 1f).coerceAtLeast(15f)
                preferences.edit().putFloat("readerFontSize", fontSize).apply()
            },
            onFontLarger = {
                fontSize = (fontSize + 1f).coerceAtMost(28f)
                preferences.edit().putFloat("readerFontSize", fontSize).apply()
            },
            onBookmark = { chapter, verse ->
                bookmarkEntries = BookmarkStore.toggle(context, bookmarkEntries, chapter, verse)
            },
            onShare = { chapter, verse -> shareVerse(context, chapter, verse) },
        )
        }
    }
}

@Composable
private fun BooksScreen(
    language: String,
    translations: List<TranslationSummary>,
    selectedTranslationCode: String,
    state: LoadState<List<BibleBook>>,
    onTranslationChange: (String) -> Unit,
    onBookClick: (BibleBook) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val oldTestamentTitle = text(R.string.bible_old_testament, language)
    val newTestamentTitle = text(R.string.bible_new_testament, language)
    val allBooksTitle = text(R.string.bible_books_title, language)
    ReaderPage(
        title = allBooksTitle,
        onBack = onBack,
    ) {
        item {
            Text(
                text(R.string.bible_translation, language),
                color = Ink,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                translations.forEach { translation ->
                    FilterChip(
                        selected = translation.code == selectedTranslationCode,
                        onClick = { onTranslationChange(translation.code) },
                        label = { Text(translation.shortName ?: translation.language.code.uppercase()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Navy,
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Ink,
                        ),
                    )
                }
            }
        }

        when (state) {
            LoadState.Loading -> item { LoadingBox() }
            LoadState.Error -> item {
                ErrorBox(text(R.string.bible_books_error, language), text(R.string.retry, language), onRetry)
            }
            is LoadState.Ready -> {
                val oldTestament = state.value.filter { it.canonicalBook?.testament == "old" }
                val newTestament = state.value.filter { it.canonicalBook?.testament == "new" }
                testamentItems(oldTestamentTitle, oldTestament, onBookClick)
                testamentItems(newTestamentTitle, newTestament, onBookClick)
                val other = state.value.filter { it !in oldTestament && it !in newTestament }
                if (other.isNotEmpty()) testamentItems(allBooksTitle, other, onBookClick)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.testamentItems(
    title: String,
    books: List<BibleBook>,
    onBookClick: (BibleBook) -> Unit,
) {
    if (books.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            color = Navy,
            fontFamily = FontFamily.Serif,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    items(books, key = BibleBook::slug) { book ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBookClick(book) }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(LightBlue, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(book.order.toString(), color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                book.name,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                color = Ink,
                fontWeight = FontWeight.SemiBold,
            )
            Text(book.chaptersCount.toString(), color = PrimaryBlue, fontSize = 12.sp)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = PrimaryBlue)
        }
        HorizontalDivider(color = WarmBorder)
    }
}

@Composable
private fun ChaptersScreen(
    language: String,
    book: BibleBook,
    onChapterClick: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ReaderHeader(book.name, onBack)
        Text(
            text(R.string.bible_chapters, language),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            color = Ink,
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(58.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems((1..book.chaptersCount).toList()) { chapter ->
                Surface(
                    modifier = Modifier
                        .size(58.dp)
                        .clickable { onChapterClick(chapter) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarmBorder),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(chapter.toString(), color = Navy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterScreen(
    language: String,
    state: LoadState<BibleChapter>,
    chapterNumber: Int,
    chaptersCount: Int,
    fontSize: Float,
    bookmarkedKeys: Set<String>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onBookmark: (BibleChapter, BibleVerse) -> Unit,
    onShare: (BibleChapter, BibleVerse) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val title = (state as? LoadState.Ready)?.value?.let {
            "${it.book.name} · ${text(R.string.bible_chapter, language, it.chapter.number)}"
        } ?: text(R.string.bible_chapter, language, chapterNumber)
        ReaderHeader(title, onBack)

        when (state) {
            LoadState.Loading -> LoadingBox(Modifier.weight(1f))
            LoadState.Error -> ErrorBox(
                text(R.string.bible_chapter_error, language),
                text(R.string.retry, language),
                onRetry,
                Modifier.weight(1f),
            )
            is LoadState.Ready -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                items(state.value.verses, key = BibleVerse::osisRef) { verse ->
                    VerseRow(
                        language = language,
                        chapter = state.value,
                        verse = verse,
                        fontSize = fontSize,
                        bookmarked = "${state.value.translation.code}:${verse.osisRef}" in bookmarkedKeys,
                        onBookmark = { onBookmark(state.value, verse) },
                        onShare = { onShare(state.value, verse) },
                    )
                }
            }
        }

        Surface(color = Color.White, shadowElevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevious, enabled = chapterNumber > 1) {
                    Icon(Icons.Outlined.ChevronLeft, text(R.string.bible_previous_chapter, language))
                }
                IconButton(onClick = onFontSmaller, enabled = fontSize > 15f) {
                    Icon(Icons.Outlined.TextDecrease, text(R.string.bible_font_smaller, language))
                }
                Text("$chapterNumber / $chaptersCount", color = Navy, fontWeight = FontWeight.Bold)
                IconButton(onClick = onFontLarger, enabled = fontSize < 28f) {
                    Icon(Icons.Outlined.TextIncrease, text(R.string.bible_font_larger, language))
                }
                IconButton(onClick = onNext, enabled = chapterNumber < chaptersCount) {
                    Icon(Icons.Outlined.ChevronRight, text(R.string.bible_next_chapter, language))
                }
            }
        }
    }
}

@Composable
private fun VerseRow(
    language: String,
    chapter: BibleChapter,
    verse: BibleVerse,
    fontSize: Float,
    bookmarked: Boolean,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            verse.number.toString(),
            modifier = Modifier.padding(top = 3.dp),
            color = PrimaryBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            verse.plainText,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            color = Ink,
            fontFamily = FontFamily.Serif,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.45f).sp,
        )
        Column {
            IconButton(onClick = onBookmark, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (bookmarked) text(R.string.bible_bookmark_remove, language) else text(R.string.bible_bookmark_add, language),
                    tint = if (bookmarked) Navy else PrimaryBlue,
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Share, text(R.string.bible_share, language), tint = PrimaryBlue)
            }
        }
    }
    HorizontalDivider(color = WarmBorder.copy(alpha = 0.7f))
}

@Composable
private fun ReaderPage(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ReaderHeader(title, onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            content = content,
        )
    }
}

@Composable
private fun ReaderHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Navy)
        }
        Text(
            title,
            modifier = Modifier.weight(1f).padding(end = 48.dp),
            color = Ink,
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier.fillMaxWidth().height(220.dp)) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Navy)
    }
}

@Composable
private fun ErrorBox(
    message: String,
    retry: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = Ink)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(retry) }
    }
}

private fun shareVerse(context: Context, chapter: BibleChapter, verse: BibleVerse) {
    val message = "${verse.plainText}\n\n${chapter.book.name} ${chapter.chapter.number}:${verse.number} · ${chapter.translation.shortName ?: chapter.translation.name}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun text(@StringRes id: Int, language: String, vararg args: Any): String {
    val context = LocalContext.current
    return remember(id, language, args.toList()) {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        context.createConfigurationContext(configuration).resources.getString(id, *args)
    }
}
