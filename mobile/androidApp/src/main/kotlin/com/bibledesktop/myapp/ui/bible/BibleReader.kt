package com.bibledesktop.myapp.ui.bible

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibledesktop.myapp.R
import com.bibledesktop.myapp.ui.reading.ReadingHeader
import com.bibledesktop.myapp.ui.theme.Cream
import com.bibledesktop.myapp.ui.theme.Ink
import com.bibledesktop.myapp.ui.theme.Navy
import com.bibledesktop.shared.api.BibleContentSource
import com.bibledesktop.shared.api.BibleBook
import com.bibledesktop.shared.api.BibleChapter
import com.bibledesktop.shared.api.BibleVerse
import com.bibledesktop.shared.api.TranslationSummary
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data object Error : LoadState<Nothing>
}

@Composable
fun BibleReader(
    language: String,
    translations: List<TranslationSummary>,
    client: BibleContentSource,
    onBack: () -> Unit,
    onDownloads: () -> Unit = {},
    choosePassageOnOpen: Boolean = false,
) {
    val context = LocalContext.current
    val pickerState = rememberSaveableStateHolder()
    val notesErrorText = text(R.string.notes_error, language)
    val noteScope = rememberCoroutineScope()
    val preferences = remember {
        context.getSharedPreferences("bible-desktop-native-profile", Context.MODE_PRIVATE)
    }
    var translationCode by rememberSaveable {
        val saved = preferences.getString("lastTranslation", null)
        mutableStateOf(
            saved?.takeIf(String::isNotBlank)
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
    var notePassage by rememberSaveable(stateSaver = Saver<BookmarkEntry?, String>(
        save = { it?.toJson()?.toString() }, restore = { bookmarkFromJson(org.json.JSONObject(it)) },
    )) { mutableStateOf<BookmarkEntry?>(null) }
    var noteInitial by rememberSaveable { mutableStateOf("") }
    var studyVerseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var focusedVerse by rememberSaveable { mutableStateOf(preferences.getInt("lastVerse", 0)) }
    var referenceReturn by rememberSaveable { mutableStateOf<String?>(null) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var compareCode by rememberSaveable { mutableStateOf(preferences.getString("compareTranslation", "").orEmpty()) }
    var choosingPassage by rememberSaveable { mutableStateOf(choosePassageOnOpen) }
    var choosingBooks by rememberSaveable { mutableStateOf(true) }
    val openError = text(R.string.study_open_error, language)
    fun chapterBack() {
        val saved = referenceReturn?.split('|')
        if (saved != null) {
            selectedBookSlug = saved[0]; selectedChapter = saved[1].toInt(); focusedVerse = saved[2].toInt(); referenceReturn = null
        } else selectedChapter = null
    }

    LaunchedEffect(translations, translationCode) {
        if (translations.isNotEmpty() && translationCode.isBlank()) {
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

    LaunchedEffect(translationCode, selectedBookSlug, selectedChapter, focusedVerse) {
        val bookSlug = selectedBookSlug ?: return@LaunchedEffect
        val chapterNumber = selectedChapter ?: return@LaunchedEffect
        preferences.edit()
            .putString("lastTranslation", translationCode)
            .putString("lastBookSlug", bookSlug)
            .putInt("lastChapter", chapterNumber)
            .putInt("lastVerse", focusedVerse)
            .apply()
    }

    val selectedBook = (booksState as? LoadState.Ready)?.value
        ?.firstOrNull { it.slug == selectedBookSlug }

    BackHandler {
        when {
            comparing -> comparing = false
            selectedChapter != null -> chapterBack()
            selectedBookSlug != null -> selectedBookSlug = null
            else -> onBack()
        }
    }

    if (translations.isEmpty() || translations.none { it.code == translationCode }) {
        Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
            ReadingHeader(text(R.string.section_bible, language), language, onBack, onBack)
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text(R.string.catalog_empty, language))
                Button(onClick = onDownloads, modifier = Modifier.testTag("reader-install")) { Text(text(R.string.catalog_add, language)) }
            }
        }
        return
    }

    when {
        selectedBook == null -> pickerState.SaveableStateProvider("book-picker") {
            BooksScreen(
                language = language,
                translations = translations,
                selectedTranslationCode = translationCode,
                state = booksState,
                onTranslationChange = {
                    translationCode = it
                    selectedBookSlug = null
                    selectedChapter = null
                    focusedVerse = 0; referenceReturn = null
                },
                onBookClick = {
                    selectedBookSlug = it.slug
                    selectedChapter = null
                    focusedVerse = 0; referenceReturn = null
                },
                onRetry = { booksRetry += 1 },
                onBack = onBack,
                onDownloads = onDownloads,
            )
        }

        selectedChapter == null -> ChaptersScreen(
            language = language,
            book = selectedBook,
            onChapterClick = { selectedChapter = it; focusedVerse = 0 },
            onBack = { selectedBookSlug = null },
            onHome = onBack,
            textLanguage = translations.firstOrNull { it.code == translationCode }?.language?.code.orEmpty(),
            onChooseBook = { choosingBooks = true; choosingPassage = true },
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
            onBack = { if (comparing) comparing = false else chapterBack() },
            onHome = onBack,
            onRetry = { chapterRetry += 1 },
            onPrevious = { selectedChapter = (chapterNumber - 1).coerceAtLeast(1); focusedVerse = 0 },
            onNext = { selectedChapter = (chapterNumber + 1).coerceAtMost(selectedBook.chaptersCount); focusedVerse = 0 },
            initialVerse = focusedVerse,
            onStudy = { _, verse -> studyVerseId = verse.id; focusedVerse = verse.number },
            comparison = if (comparing) {
                { value, modifier -> ComparisonPane(language, value, translations, compareCode, { code ->
                    compareCode = code; preferences.edit().putString("compareTranslation", code).apply()
                }, client, fontSize, focusedVerse, modifier) }
            } else null,
            comparing = comparing,
            onCompare = {
                if (!comparing && (translations.none { it.code == compareCode } || compareCode == translationCode))
                    compareCode = translations.firstOrNull { it.code != translationCode }?.code.orEmpty()
                comparing = !comparing
            },
            onDownloads = onDownloads,
            textLanguage = translations.firstOrNull { it.code == translationCode }?.language?.code.orEmpty(),
            onChooseBook = { choosingBooks = true; choosingPassage = true },
            onChooseChapter = { choosingBooks = false; choosingPassage = true },
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
            onShare = { chapter, verse -> shareBiblePassage(context, versePassage(chapter, verse)) },
            onNote = { chapter, verse ->
                val passage = versePassage(chapter, verse)
                noteScope.launch {
                    try {
                        noteInitial = NoteStore.read(context).firstOrNull {
                            it.passage.reference == passage.reference && it.passage.translationCode == passage.translationCode
                        }?.body.orEmpty()
                        notePassage = passage
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        android.widget.Toast.makeText(context, notesErrorText, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
        }
    }
    if (choosingPassage) PassagePicker(language, translations, client, translationCode, selectedBook, selectedChapter,
        choosingBooks, onSelect = { code, book, number ->
            translationCode = code; selectedBookSlug = book.slug; selectedChapter = number
            focusedVerse = 0; referenceReturn = null; studyVerseId = null; choosingPassage = false
        }, onClose = { choosingPassage = false }, onHome = { choosingPassage = false; onBack() },
        initialBookSlug = selectedBookSlug)
    notePassage?.let { passage ->
        NoteEditor(language, passage, noteInitial, onDismiss = { notePassage = null }, onSaved = { notePassage = null })
    }
    val chapter = (chapterState as? LoadState.Ready)?.value
    chapter?.verses?.firstOrNull { it.id == studyVerseId }?.let { verse ->
        com.bibledesktop.myapp.ui.study.VerseStudyDialog(language, chapter, verse, client, onClose = { studyVerseId = null }, onOpen = { target ->
            noteScope.launch {
                try {
                    val book = client.getBooks(translationCode).firstOrNull { it.canonicalBook?.osisCode == target.osisRef.substringBefore('.') }
                        ?: error("Canonical book unavailable")
                    require(target.chapterNumber in 1..book.chaptersCount)
                    referenceReturn = "$selectedBookSlug|$selectedChapter|${verse.number}"
                    selectedBookSlug = book.slug; selectedChapter = target.chapterNumber; focusedVerse = target.verseNumber; studyVerseId = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { android.widget.Toast.makeText(context, openError, android.widget.Toast.LENGTH_LONG).show() }
            }
        })
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
    onDownloads: () -> Unit,
) {
    BookPicker(
        language, translations, selectedTranslationCode,
        books = (state as? LoadState.Ready)?.value,
        error = state is LoadState.Error,
        onTranslationChange, onBookClick, onRetry, onBack,
        onAddBibles = onDownloads,
    )
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
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onBookmark: (BibleChapter, BibleVerse) -> Unit,
    onShare: (BibleChapter, BibleVerse) -> Unit,
    onNote: (BibleChapter, BibleVerse) -> Unit,
    onStudy: (BibleChapter, BibleVerse) -> Unit,
    initialVerse: Int,
    comparison: (@Composable (BibleChapter, Modifier) -> Unit)?,
    comparing: Boolean,
    onCompare: () -> Unit,
    onDownloads: () -> Unit,
    textLanguage: String,
    onChooseBook: () -> Unit,
    onChooseChapter: () -> Unit,
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
        ReadingHeader(title, language, onBack, onHome, com.bibledesktop.myapp.ui.theme.readingFont(textLanguage))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onChooseBook, modifier = Modifier.weight(1f).testTag("reader-choose-book")) {
                Text(text(R.string.bible_choose_book, language))
            }
            androidx.compose.material3.OutlinedButton(onClick = onChooseChapter, modifier = Modifier.testTag("reader-choose-chapter")) {
                Text(text(R.string.bible_chapter, language, chapterNumber) + " ▾")
            }
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
            androidx.compose.material3.TextButton(onClick = onCompare) { Text(text(if (comparing) R.string.compare_close else R.string.compare_open, language)) }
            androidx.compose.material3.TextButton(onClick = onDownloads) { Text(text(R.string.bible_library_title, language)) }
        }

        when (state) {
            LoadState.Loading -> LoadingBox(Modifier.weight(1f))
            LoadState.Error -> ErrorBox(
                text(R.string.catalog_local_missing, language),
                text(R.string.catalog_add, language),
                onDownloads,
                Modifier.weight(1f),
            )
            is LoadState.Ready -> if (comparison != null) comparison(state.value, Modifier.weight(1f)) else if (state.value.verses.isEmpty()) ErrorBox(
                text(R.string.bible_chapter_unavailable, language), text(R.string.retry, language), onRetry, Modifier.weight(1f),
            ) else ChapterReadingContent(
                language, state.value, fontSize, bookmarkedKeys, onBookmark, onShare, onNote,
                modifier = Modifier.weight(1f),
                onStudy = onStudy, initialVerse = initialVerse,
            )
        }

        Surface(color = Color.White, shadowElevation = 6.dp) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious, enabled = chapterNumber > 1) {
                        Icon(Icons.Outlined.ChevronLeft, text(R.string.bible_previous_chapter, language))
                    }
                    IconButton(onClick = onFontSmaller, enabled = fontSize > 15f) {
                        Icon(Icons.Outlined.TextDecrease, text(R.string.bible_font_smaller, language))
                    }
                    androidx.compose.material3.TextButton(onClick = onChooseChapter) {
                        Text("$chapterNumber / $chaptersCount ▾", color = Navy, fontWeight = FontWeight.Bold)
                    }
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

@Composable
private fun text(@StringRes id: Int, language: String, vararg args: Any): String {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    return remember(id, language, args.toList(), currentConfiguration) {
        val configuration = Configuration(currentConfiguration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        context.createConfigurationContext(configuration).resources.getString(id, *args)
    }
}
