package com.bibledesktop.myapp.ui.bible

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
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
import com.bibledesktop.shared.api.SavedPassage
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

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
    val (controlPreferences,setControlPreferences)=rememberReaderPreferences()
    LaunchedEffect(controlPreferences.fontSize) {fontSize=controlPreferences.fontSize}
    var settingsOpen by remember {mutableStateOf(false)}
    var translationsOpen by remember {mutableStateOf(false)}
    var historyOpen by remember {mutableStateOf(false)}
    val history=remember {ReaderHistoryStore(context,"main")}
    var historyRestoring by remember {mutableStateOf(false)}
    var firstHistoryOpening by remember {mutableStateOf(true)}
    var bookmarkEntries by remember { mutableStateOf(BookmarkStore.load(context)) }
    var notePassage by rememberSaveable(stateSaver = Saver<BookmarkEntry?, String>(
        save = { it?.toJson()?.toString() }, restore = { bookmarkFromJson(org.json.JSONObject(it)) },
    )) { mutableStateOf<BookmarkEntry?>(null) }
    var noteInitial by rememberSaveable { mutableStateOf("") }
    var studyVerseId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showingSearch by rememberSaveable { mutableStateOf(false) }
    var personalChapter by remember { mutableStateOf<BibleChapter?>(null) }
    var personalVerse by remember { mutableStateOf<BibleVerse?>(null) }
    var selectedPassage by remember { mutableStateOf<SavedPassage?>(null) }
    var personalLibrary by remember { mutableStateOf(false) }
    var sourceInfoOpen by remember { mutableStateOf(false) }
    var initialStrong by remember { mutableStateOf<String?>(null) }
    var studyChapter by remember { mutableStateOf<BibleChapter?>(null) }
    var focusedVerse by rememberSaveable { mutableStateOf(preferences.getInt("lastVerse", 0)) }
    var readingChapter by remember { mutableIntStateOf(selectedChapter ?: 1) }
    var readingVerse by remember { mutableIntStateOf(focusedVerse) }
    var restoringPosition by remember { mutableStateOf(true) }
    fun historyNavigate(place: ReaderHistoryPlace?) {
        if(place==null)return
        historyRestoring=true
        restoringPosition=true
        translationCode=place.code;selectedBookSlug=place.book;selectedChapter=place.chapter;focusedVerse=place.verse
        preferences.edit().putString("lastTranslation",place.code).putString("lastBookSlug",place.book).putInt("lastChapter",place.chapter).putInt("lastVerse",place.verse).putInt("lastVerseOffset",place.offset).apply()
    }
    var referenceReturn by rememberSaveable { mutableStateOf<String?>(null) }
    var comparing by rememberSaveable { mutableStateOf(preferences.getBoolean("readerComparing", false)) }
    var windowCommands by remember { mutableStateOf<WindowCommands?>(null) }
    var windowChapter by remember { mutableStateOf<BibleChapter?>(null) }
    var windowLast by remember { mutableIntStateOf(0) }
    fun closeComparison() {
        windowChapter?.let { source -> translationCode = source.translation.code; selectedBookSlug = source.book.slug; selectedChapter = readingChapter; focusedVerse = readingVerse; restoringPosition = true }
        comparing = false; preferences.edit().putBoolean("readerComparing", false).apply()
    }
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

    if (showingSearch) {
        BibleSearchScreen(language, client, translationCode, onBack = { showingSearch = false }, onOpen = { hit ->
            restoringPosition = false
            translationCode = hit.translation; selectedBookSlug = hit.book; selectedChapter = hit.chapter; focusedVerse = hit.verse
            preferences.edit().putString("lastTranslation",hit.translation).putString("lastBookSlug",hit.book)
                .putInt("lastChapter",hit.chapter).putInt("lastVerse",hit.verse).putInt("lastVerseOffset",0).apply()
            showingSearch = false
        })
        return
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
        readingChapter = chapterNumber
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
        val editor = preferences.edit()
            .putString("lastTranslation", translationCode)
            .putString("lastBookSlug", bookSlug)
            .putInt("lastChapter", chapterNumber)
            .putInt("lastVerse", focusedVerse)
        if (!restoringPosition) editor.putInt("lastVerseOffset", 0)
        editor.apply()
        restoringPosition = false
    }

    val selectedBook = (booksState as? LoadState.Ready)?.value
        ?.firstOrNull { it.slug == selectedBookSlug }
    LaunchedEffect(chapterState,focusedVerse) {
        val source=(chapterState as? LoadState.Ready)?.value ?: return@LaunchedEffect
        if(source.translation.code!=translationCode || source.book.slug!=selectedBookSlug || source.chapter.number!=selectedChapter)return@LaunchedEffect
        if(!firstHistoryOpening&&!historyRestoring)history.navigate(ReaderHistoryPlace(source.translation.code,source.book.slug,source.chapter.number,focusedVerse.coerceAtLeast(1)))
        firstHistoryOpening=false;historyRestoring=false
    }

    BackHandler {
        when {
            comparing -> closeComparison()
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
            ReaderTheme(controlPreferences) {CompositionLocalProvider(com.bibledesktop.myapp.ui.study.LocalVerseStudyClient provides client, LocalReaderNavigationActions provides ReaderNavigationActions(
                chapter={delta->if(comparing)windowCommands?.move?.invoke(delta) else {selectedChapter=(readingChapter+delta).coerceIn(1,selectedBook.chaptersCount);focusedVerse=1}},
                book={delta->val list=(booksState as? LoadState.Ready)?.value.orEmpty();val next=list.getOrNull(list.indexOfFirst{it.slug==selectedBookSlug}+delta);next?.let{selectedBookSlug=it.slug;selectedChapter=1;focusedVerse=1}}
            )) {ChapterScreen(
            language = language,
            studyClient = client,
            state = chapterState,
            chapterNumber = readingChapter,
            chaptersCount = if (comparing) windowChapter?.book?.chaptersCount ?: selectedBook.chaptersCount else selectedBook.chaptersCount,
            fontSize = fontSize,
            bookmarkedKeys = bookmarkEntries.map { "${it.translationCode}:${it.reference}" }.toSet(),
            onBack = { if (comparing) closeComparison() else chapterBack() },
            onHome = onBack,
            onRetry = { chapterRetry += 1 },
            onSearch = { if (comparing) closeComparison(); restoringPosition = true; selectedChapter = readingChapter; focusedVerse = readingVerse; showingSearch = true },
            onSettings={settingsOpen=true},
            onToggleNight={setControlPreferences(controlPreferences.copy(night=!controlPreferences.night))},
            onSourceInfo={sourceInfoOpen=true},
            onTranslations={translationsOpen=true},
            onHistory={if(comparing)windowCommands?.history?.invoke() else historyOpen=true},
            onHistoryBack={if(comparing)windowCommands?.back?.invoke() else historyNavigate(history.back())},
            onHistoryForward={if(comparing)windowCommands?.forward?.invoke() else historyNavigate(history.forward())},
            onPrevious = { if (comparing && windowCommands != null) windowCommands?.move?.invoke(-1) else { selectedChapter = (readingChapter - 1).coerceAtLeast(1); focusedVerse = 0 } },
            onNext = { if (comparing && windowCommands != null) windowCommands?.move?.invoke(1) else { selectedChapter = (readingChapter + 1).coerceAtMost(selectedBook.chaptersCount); focusedVerse = 0 } },
            initialVerse = focusedVerse,
            comparisonSource = if (comparing) windowChapter else null,
            comparisonVerse = readingVerse,
            comparisonLast = windowLast,
            onStudy = { source, verse -> initialStrong = null; studyChapter = source; studyVerseId = verse.id },
            onPersonal = { source, verse -> personalChapter = source; personalVerse = verse },
            selection = selectedPassage,
            onStrong = { source, verse, number -> initialStrong = number; studyChapter = source; studyVerseId = verse.id },
            onVisiblePlace = { source, first, _, offset ->
                // An old list can finish its delayed observation while a new passage is opening.
                if (source.translation.code == translationCode && source.book.slug == selectedBookSlug && selectedChapter == chapterNumber) {
                    readingChapter = source.chapter.number; readingVerse = first.number
                    history.observe(ReaderHistoryPlace(source.translation.code,source.book.slug,source.chapter.number,first.number,offset))
                    preferences.edit().putString("lastTranslation", source.translation.code).putString("lastBookSlug", source.book.slug)
                        .putInt("lastChapter", source.chapter.number).putInt("lastVerse", first.number).putInt("lastVerseOffset", offset).apply()
                }
            },
            comparison = if (comparing) {
                { value, modifier -> ComparisonPane(language, value, translations, compareCode, { code ->
                    compareCode = code; preferences.edit().putString("compareTranslation", code).apply()
                }, client, fontSize, focusedVerse, modifier,
                    bookmarks = bookmarkEntries.map { "${it.translationCode}:${it.reference}" }.toSet(),
                    onBookmark = { source, verse -> bookmarkEntries = BookmarkStore.toggle(context, bookmarkEntries, source, verse) },
                    onShare = { source, verse -> shareBiblePassage(context, versePassage(source, verse)) },
                    onNote = { source, verse -> val passage = versePassage(source, verse); noteScope.launch {
                        try { noteInitial = NoteStore.read(context).firstOrNull { it.passage.reference == passage.reference && it.passage.translationCode == passage.translationCode }?.body.orEmpty(); notePassage = passage }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { android.widget.Toast.makeText(context, notesErrorText, android.widget.Toast.LENGTH_LONG).show() }
                    } },
                    onStudy = { source, verse -> initialStrong = null; studyChapter = source; studyVerseId = verse.id },
            onPersonal = { source, verse -> personalChapter = source; personalVerse = verse },
            selection = selectedPassage,
            onStrong = { source, verse, number -> initialStrong = number; studyChapter = source; studyVerseId = verse.id },
                    onVisible = { source, first, last, offset -> windowChapter = source; windowLast = last.number; readingChapter = source.chapter.number; readingVerse = first.number
                        preferences.edit().putString("lastTranslation", source.translation.code).putString("lastBookSlug", source.book.slug)
                            .putInt("lastChapter", source.chapter.number).putInt("lastVerse", first.number).putInt("lastVerseOffset", offset).apply() },
                    onCommands = { commands -> windowCommands = commands }) }
            } else null,
            comparing = comparing,
            onCompare = {
                if (comparing) closeComparison() else {
                    selectedChapter = readingChapter; focusedVerse = readingVerse
                    if (translations.none { it.code == compareCode } || compareCode == translationCode)
                        compareCode = translations.firstOrNull { it.code != translationCode }?.code ?: translationCode
                    comparing = true; preferences.edit().putBoolean("readerComparing", true).apply()
                }
            },
            onDownloads = onDownloads,
            textLanguage = translations.firstOrNull { it.code == translationCode }?.language?.code.orEmpty(),
            onChooseBook = { if (comparing && windowCommands != null) windowCommands?.choose?.invoke(true) else { choosingBooks = true; choosingPassage = true } },
            onChooseChapter = { if (comparing && windowCommands != null) windowCommands?.choose?.invoke(false) else { choosingBooks = false; choosingPassage = true } },
            onFontSmaller = {
                fontSize = (fontSize - 1f).coerceAtLeast(15f)
                preferences.edit().putFloat("readerFontSize", fontSize).apply()
                setControlPreferences(controlPreferences.copy(fontSize=fontSize))
            },
            onFontLarger = {
                fontSize = (fontSize + 1f).coerceAtMost(28f)
                preferences.edit().putFloat("readerFontSize", fontSize).apply()
                setControlPreferences(controlPreferences.copy(fontSize=fontSize))
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
        )} }
        }
    }
    if (choosingPassage) PassagePicker(language, translations, client, translationCode, selectedBook, selectedChapter,
        choosingBooks, onSelect = { code, book, number ->
            translationCode = code; selectedBookSlug = book.slug; selectedChapter = number
            focusedVerse = 0; referenceReturn = null; studyVerseId = null; choosingPassage = false
        }, onClose = { choosingPassage = false }, onHome = { choosingPassage = false; onBack() },
        initialBookSlug = selectedBookSlug,onVerseSelect={code,book,number,verse ->
            translationCode=code;selectedBookSlug=book.slug;selectedChapter=number;focusedVerse=verse;referenceReturn=null;studyVerseId=null;choosingPassage=false
        })
    if(settingsOpen)ReaderSettingsDialog(language,controlPreferences,setControlPreferences){settingsOpen=false}
    if(translationsOpen)TranslationPicker(language,translations,if(comparing)windowChapter?.translation?.code?:translationCode else translationCode,onSelect={code->
        translationsOpen=false
        if(comparing)windowCommands?.translation?.invoke(code) else noteScope.launch {
            val source=(chapterState as? LoadState.Ready)?.value ?: return@launch
            val ref=source.verses.firstOrNull{it.number==readingVerse}?.osisRef ?: source.verses.firstOrNull()?.osisRef ?: return@launch
            try {
                val location=client.getVerseLocations(code,listOf(ref)).firstOrNull() ?: error("Verse unavailable")
                val target=client.getChapter(code,location.book,location.chapter)
                val verse=target.verses.firstOrNull{it.osisRef==ref && it.plainText.isNotBlank()} ?: error("Verse unavailable")
                translationCode=code;selectedBookSlug=target.book.slug;selectedChapter=target.chapter.number;focusedVerse=verse.number
            } catch(cancelled:CancellationException){throw cancelled} catch(_:Exception){android.widget.Toast.makeText(context,readerControlText(language,"invalid"),android.widget.Toast.LENGTH_LONG).show()}
        }
    },onClose={translationsOpen=false})
    if(historyOpen)ReaderHistoryDialog(language,history,::historyNavigate){historyOpen=false}
    notePassage?.let { passage ->
        NoteEditor(language, passage, noteInitial, onDismiss = { notePassage = null }, onSaved = { notePassage = null })
    }
    if (personalLibrary) androidx.compose.ui.window.Dialog(onDismissRequest = { personalLibrary = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) { Surface(androidx.compose.ui.Modifier.fillMaxSize()) { PersonalStudyLibrary(language, onBack = { personalLibrary = false }, onOpen = { passage ->
        if (comparing) closeComparison()
        translationCode = passage.translationCode; selectedBookSlug = passage.bookSlug; selectedChapter = passage.start.chapter; focusedVerse = passage.start.verse; selectedPassage = passage; personalLibrary = false
    }) } }
    personalChapter?.let { source -> personalVerse?.let { verse -> PersonalStudyPanel(language, source, verse, client, onClose = { personalChapter = null; personalVerse = null }, onSelection = { selectedPassage = it }, onLibrary = { personalChapter = null; personalVerse = null; personalLibrary = true }) } }
    if(sourceInfoOpen) {
        val actual=if(comparing)windowCommands?.source?.invoke()?:windowChapter else (chapterState as? LoadState.Ready)?.value
        actual?.let { value -> androidx.compose.material3.AlertDialog(onDismissRequest={sourceInfoOpen=false},
            confirmButton={androidx.compose.material3.TextButton(onClick={sourceInfoOpen=false}){Text(text(R.string.study_close,language))}},
            text={Column(Modifier.verticalScroll(rememberScrollState())) {com.bibledesktop.myapp.ui.study.ModuleSourceCard(language,Json.encodeToJsonElement(translations.firstOrNull{it.code==value.translation.code}?:value.translation).jsonObject)}}) }
    }
    val chapter = studyChapter ?: (chapterState as? LoadState.Ready)?.value
    chapter?.verses?.firstOrNull { it.id == studyVerseId }?.let { verse ->
        CompositionLocalProvider(com.bibledesktop.myapp.ui.study.LocalTemporaryWindowAssignment provides
            windowCommands?.let { commands -> { source: BibleChapter, number: Int, id: Int -> commands.preview(source,number,id); studyVerseId=null } }) {
        com.bibledesktop.myapp.ui.study.VerseStudyDialog(language, chapter, verse, client, initialStrong = initialStrong, onClose = { studyVerseId = null }, onOpen = { target ->
            noteScope.launch {
                try {
                    val book = client.getBooks(translationCode).firstOrNull { it.canonicalBook?.osisCode == target.osisRef.substringBefore('.') }
                        ?: error("Canonical book unavailable")
                    require(target.chapterNumber in 1..book.chaptersCount)
                    referenceReturn = "${chapter.book.slug}|${chapter.chapter.number}|${verse.number}"
                    selectedBookSlug = book.slug; selectedChapter = target.chapterNumber; focusedVerse = target.verseNumber; studyVerseId = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { android.widget.Toast.makeText(context, openError, android.widget.Toast.LENGTH_LONG).show() }
            }
        })
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
    studyClient: BibleContentSource,
    state: LoadState<BibleChapter>,
    chapterNumber: Int,
    chaptersCount: Int,
    fontSize: Float,
    bookmarkedKeys: Set<String>,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onToggleNight: () -> Unit,
    onSourceInfo: () -> Unit,
    onTranslations: () -> Unit,
    onHistory: () -> Unit,
    onHistoryBack: () -> Unit,
    onHistoryForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
    onBookmark: (BibleChapter, BibleVerse) -> Unit,
    onShare: (BibleChapter, BibleVerse) -> Unit,
    onNote: (BibleChapter, BibleVerse) -> Unit,
    onStudy: (BibleChapter, BibleVerse) -> Unit,
    onPersonal: (BibleChapter, BibleVerse) -> Unit,
    selection: SavedPassage?,
    onStrong: (BibleChapter, BibleVerse, String) -> Unit,
    onVisiblePlace: (BibleChapter, BibleVerse, BibleVerse, Int) -> Unit,
    initialVerse: Int,
    comparison: (@Composable (BibleChapter, Modifier) -> Unit)?,
    comparing: Boolean,
    onCompare: () -> Unit,
    onDownloads: () -> Unit,
    textLanguage: String,
    onChooseBook: () -> Unit,
    onChooseChapter: () -> Unit,
    comparisonSource: BibleChapter? = null,
    comparisonVerse: Int = 0,
    comparisonLast: Int = comparisonVerse,
) {
    var showingCommentaries by rememberSaveable { mutableStateOf(false) }
    var visibleFirst by remember(state) { mutableIntStateOf(initialVerse) }
    var visibleLast by remember(state) { mutableIntStateOf(initialVerse) }
    var visibleChapter by remember(state) { mutableStateOf<BibleChapter?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        val title = (comparisonSource ?: (state as? LoadState.Ready)?.value)?.let {
            val verse = if (comparisonSource != null) comparisonVerse else visibleFirst
            "${it.book.name} · ${text(R.string.bible_chapter, language, chapterNumber)}${if (verse > 0) ":$verse" else ""}"
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
            androidx.compose.material3.TextButton(onClick = onSearch) { Text(text(R.string.verse_search_title, language)) }
            androidx.compose.material3.TextButton(onClick = { showingCommentaries = !showingCommentaries }) { Text(text(R.string.study_commentaries, language)) }
            androidx.compose.material3.TextButton(onClick=onSettings) {Text(readerControlText(language,"settings"))}
            androidx.compose.material3.TextButton(onClick=onToggleNight) {val night=LocalReaderPreferences.current.night;Text("${if(night) "☀" else "☾"} ${readerControlText(language,if(night) "day" else "night")}")}
            androidx.compose.material3.TextButton(onClick=onSourceInfo) {Text(com.bibledesktop.myapp.ui.study.moduleSourceTitle(language))}
            androidx.compose.material3.TextButton(onClick=onTranslations) {Text(readerControlText(language,"favorites"))}
            androidx.compose.material3.TextButton(onClick=onHistoryBack) {Text(readerControlText(language,"back"))}
            androidx.compose.material3.TextButton(onClick=onHistoryForward) {Text(readerControlText(language,"forward"))}
            androidx.compose.material3.TextButton(onClick=onHistory) {Text(readerControlText(language,"history"))}
        }

        when (state) {
            LoadState.Loading -> LoadingBox(Modifier.weight(1f))
            LoadState.Error -> ErrorBox(
                text(R.string.catalog_local_missing, language),
                text(R.string.catalog_add, language),
                onDownloads,
                Modifier.weight(1f),
            )
            is LoadState.Ready -> if (comparison != null) Column(Modifier.weight(1f)) {
                comparison(state.value, Modifier.weight(1f))
                if (showingCommentaries) {
                    val source = comparisonSource ?: state.value
                    val verse = source.verses.firstOrNull { it.number == comparisonVerse } ?: source.verses.firstOrNull()
                    if (verse != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        com.bibledesktop.myapp.ui.study.CommentaryPanel(language, source, verse, studyClient, comparisonLast)
                    }
                }
            } else if (state.value.verses.isEmpty()) ErrorBox(
                text(R.string.bible_chapter_unavailable, language), text(R.string.retry, language), onRetry, Modifier.weight(1f),
            ) else Column(Modifier.weight(1f)) {
                ChapterReadingContent(
                language, state.value, fontSize, bookmarkedKeys, onBookmark, onShare, onNote,
                modifier = Modifier.weight(1f),
                onStudy = onStudy, initialVerse = initialVerse, onPersonal = onPersonal, selection = selection, onStrong = onStrong,
                client = studyClient,
                onVisiblePlace = { source, first, last, offset -> visibleChapter = source; visibleFirst = first.number; visibleLast = last.number; onVisiblePlace(source, first, last, offset) },
            )
                if (showingCommentaries) {
                    val source = visibleChapter ?: state.value
                    val verse = source.verses.firstOrNull { it.number == visibleFirst } ?: source.verses.first()
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        com.bibledesktop.myapp.ui.study.CommentaryPanel(language, source, verse, studyClient, visibleLast)
                    }
                }
            }
        }

        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
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
